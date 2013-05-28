#include <jni.h>
#include <time.h>
#include <pthread.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#ifdef NDK_PROFILER
#include "prof.h"
#endif

#include "fitz.h"
#include "fitz-internal.h"
#include "mupdf.h"

#define JNI_FN(A) Java_lah_texpert_ ## A
#define PACKAGENAME "lah/texpert"

#define LOG_TAG "libmupdf"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGT(...) __android_log_print(ANDROID_LOG_INFO,"alert",__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

/* Set to 1 to enable debug log traces. */
#define DEBUG 0

/* Enable to log rendering times (render each frame 100 times and time) */
#undef TIME_DISPLAY_LIST

#define MAX_SEARCH_HITS (500)
#define NUM_CACHE (3)

enum
{
	NONE,
	TEXT,
	LISTBOX,
	COMBOBOX
};

typedef struct rect_node_s rect_node;

struct rect_node_s
{
	fz_rect rect;
	rect_node *next;
};

typedef struct
{
	int number;
	int width;
	int height;
	fz_rect media_box;
	fz_page *page;
	rect_node *changed_rects;
	rect_node *hq_changed_rects;
	fz_display_list *page_list;
	fz_display_list *annot_list;
} page_cache;

typedef struct globals_s globals;

struct globals_s
{
	fz_colorspace *colorspace;
	fz_document *doc;
	int resolution;
	fz_context *ctx;
	fz_rect *hit_bbox;
	int current;
	char *current_path;

	page_cache pages[NUM_CACHE];

	int alerts_initialised;
	// fin_lock and fin_lock2 are used during shutdown. The two waiting tasks
	// show_alert and waitForAlertInternal respectively take these locks while
	// waiting. During shutdown, the conditions are signaled and then the fin_locks
	// are taken momentarily to ensure the blocked threads leave the controlled
	// area of code before the mutexes and condition variables are destroyed.
	pthread_mutex_t fin_lock;
	pthread_mutex_t fin_lock2;
	// alert_lock is the main lock guarding the variables directly below.
	pthread_mutex_t alert_lock;
	// Flag indicating if the alert system is active. When not active, both
	// show_alert and waitForAlertInternal return immediately.
	int alerts_active;
	// Pointer to the alert struct passed in by show_alert, and valid while
	// show_alert is blocked.
	fz_alert_event *current_alert;
	// Flag and condition varibles to signal a request is present and a reply
	// is present, respectively. The condition variables alone are not sufficient
	// because of the pthreads permit spurious signals.
	int alert_request;
	int alert_reply;
	pthread_cond_t alert_request_cond;
	pthread_cond_t alert_reply_cond;

	// For the buffer reading mode, we need to implement stream reading, which
	// needs access to the following.
	JNIEnv *env;
	jclass thiz;
};

static jfieldID global_fid;
static jfieldID buffer_fid;

static void drop_changed_rects(fz_context *ctx, rect_node **nodePtr)
{
	rect_node *node = *nodePtr;
	while (node)
	{
		rect_node *tnode = node;
		node = node->next;
		fz_free(ctx, tnode);
	}

	*nodePtr = NULL;
}

static void drop_page_cache(globals *glo, page_cache *pc)
{
	fz_context  *ctx = glo->ctx;
	fz_document *doc = glo->doc;

	LOGI("Drop page %d", pc->number);
	fz_free_display_list(ctx, pc->page_list);
	pc->page_list = NULL;
	fz_free_display_list(ctx, pc->annot_list);
	pc->annot_list = NULL;
	fz_free_page(doc, pc->page);
	pc->page = NULL;
	drop_changed_rects(ctx, &pc->changed_rects);
	drop_changed_rects(ctx, &pc->hq_changed_rects);
}

static void dump_annotation_display_lists(globals *glo)
{
	fz_context *ctx = glo->ctx;
	int i;

	for (i = 0; i < NUM_CACHE; i++) {
		fz_free_display_list(ctx, glo->pages[i].annot_list);
		glo->pages[i].annot_list = NULL;
	}
}

static void show_alert(globals *glo, fz_alert_event *alert)
{
	pthread_mutex_lock(&glo->fin_lock2);
	pthread_mutex_lock(&glo->alert_lock);

	LOGT("Enter show_alert: %s", alert->title);
	alert->button_pressed = 0;

	if (glo->alerts_active)
	{
		glo->current_alert = alert;
		glo->alert_request = 1;
		pthread_cond_signal(&glo->alert_request_cond);

		while (glo->alerts_active && !glo->alert_reply)
			pthread_cond_wait(&glo->alert_reply_cond, &glo->alert_lock);
		glo->alert_reply = 0;
		glo->current_alert = NULL;
	}

	LOGT("Exit show_alert");

	pthread_mutex_unlock(&glo->alert_lock);
	pthread_mutex_unlock(&glo->fin_lock2);
}

static void event_cb(fz_doc_event *event, void *data)
{
	globals *glo = (globals *)data;

	switch (event->type)
	{
	case FZ_DOCUMENT_EVENT_ALERT:
		show_alert(glo, fz_access_alert_event(event));
		break;
	}
}

static void alerts_init(globals *glo)
{
	fz_interactive *idoc = fz_interact(glo->doc);

	if (!idoc || glo->alerts_initialised)
		return;

	glo->alerts_active = 0;
	glo->alert_request = 0;
	glo->alert_reply = 0;
	pthread_mutex_init(&glo->fin_lock, NULL);
	pthread_mutex_init(&glo->fin_lock2, NULL);
	pthread_mutex_init(&glo->alert_lock, NULL);
	pthread_cond_init(&glo->alert_request_cond, NULL);
	pthread_cond_init(&glo->alert_reply_cond, NULL);

	fz_set_doc_event_callback(idoc, event_cb, glo);
	LOGT("alert_init");
	glo->alerts_initialised = 1;
}

static void alerts_fin(globals *glo)
{
	fz_interactive *idoc = fz_interact(glo->doc);
	if (!glo->alerts_initialised)
		return;

	LOGT("Enter alerts_fin");
	if (idoc)
		fz_set_doc_event_callback(idoc, NULL, NULL);

	// Set alerts_active false and wake up show_alert and waitForAlertInternal,
	pthread_mutex_lock(&glo->alert_lock);
	glo->current_alert = NULL;
	glo->alerts_active = 0;
	pthread_cond_signal(&glo->alert_request_cond);
	pthread_cond_signal(&glo->alert_reply_cond);
	pthread_mutex_unlock(&glo->alert_lock);

	// Wait for the fin_locks.
	pthread_mutex_lock(&glo->fin_lock);
	pthread_mutex_unlock(&glo->fin_lock);
	pthread_mutex_lock(&glo->fin_lock2);
	pthread_mutex_unlock(&glo->fin_lock2);

	pthread_cond_destroy(&glo->alert_reply_cond);
	pthread_cond_destroy(&glo->alert_request_cond);
	pthread_mutex_destroy(&glo->alert_lock);
	pthread_mutex_destroy(&glo->fin_lock2);
	pthread_mutex_destroy(&glo->fin_lock);
	LOGT("Exit alerts_fin");
	glo->alerts_initialised = 0;
}

static globals *get_globals(JNIEnv *env, jobject thiz)
{
	globals *glo = (globals *)(void *)((*env)->GetLongField(env, thiz, global_fid));
	glo->env = env;
	glo->thiz = thiz;
	return glo;
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPDF_openFile)(JNIEnv * env, jobject thiz, jstring jfilename)
{
	const char *filename;
	globals    *glo;
	fz_context *ctx;
	jclass      clazz;

#ifdef NDK_PROFILER
	monstartup("libmupdf.so");
#endif

	clazz = (*env)->GetObjectClass(env, thiz);
	global_fid = (*env)->GetFieldID(env, clazz, "globals", "J");

	glo = calloc(1, sizeof(*glo));
	if (glo == NULL)
		return 0;
	glo->resolution = 160;
	glo->alerts_initialised = 0;

	filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
	if (filename == NULL)
	{
		LOGE("Failed to get filename");
		free(glo);
		return 0;
	}

	/* 128 MB store for low memory devices. Tweak as necessary. */
	glo->ctx = ctx = fz_new_context(NULL, NULL, 128 << 20);
	if (!ctx)
	{
		LOGE("Failed to initialise context");
		(*env)->ReleaseStringUTFChars(env, jfilename, filename);
		free(glo);
		return 0;
	}

	glo->doc = NULL;
	fz_try(ctx)
	{
		glo->colorspace = fz_device_rgb;

		LOGE("Opening document...");
		fz_try(ctx)
		{
			glo->current_path = fz_strdup(ctx, (char *)filename);
			glo->doc = fz_open_document(ctx, (char *)filename);
			alerts_init(glo);
		}
		fz_catch(ctx)
		{
			fz_throw(ctx, "Cannot open document: '%s'", filename);
		}
		LOGE("Done!");
	}
	fz_catch(ctx)
	{
		LOGE("Failed: %s", ctx->error->message);
		fz_close_document(glo->doc);
		glo->doc = NULL;
		fz_free_context(ctx);
		glo->ctx = NULL;
		free(glo);
		glo = NULL;
	}

	(*env)->ReleaseStringUTFChars(env, jfilename, filename);

	return (jlong)(void *)glo;
}

static int bufferStreamRead(fz_stream *stream, unsigned char *buf, int len)
{
	globals    *glo = (globals *)stream->state;
	JNIEnv     *env = glo->env;
	jbyteArray  array = (jbyteArray)(void *)((*env)->GetObjectField(env, glo->thiz, buffer_fid));
	int         arrayLength = (*env)->GetArrayLength(env, array);

	if (stream->pos > arrayLength)
		stream->pos = arrayLength;
	if (stream->pos < 0)
		stream->pos = 0;
	if (len + stream->pos > arrayLength)
		len = arrayLength - stream->pos;

	(*env)->GetByteArrayRegion(env, array, stream->pos, len, buf);
	(*env)->DeleteLocalRef(env, array);
	return len;
}

static void bufferStreamClose(fz_context *ctx, void *state)
{
	/* Nothing to do */
}

static void bufferStreamSeek(fz_stream *stream, int offset, int whence)
{
	globals    *glo = (globals *)stream->state;
	JNIEnv     *env = glo->env;
	jbyteArray  array = (jbyteArray)(void *)((*env)->GetObjectField(env, glo->thiz, buffer_fid));
	int         arrayLength = (*env)->GetArrayLength(env, array);

	(*env)->DeleteLocalRef(env, array);

	if (whence == 0) /* SEEK_SET */
		stream->pos = offset;
	else if (whence == 1) /* SEEK_CUR */
		stream->pos += offset;
	else if (whence == 2) /* SEEK_END */
		stream->pos = arrayLength + offset;

	if (stream->pos > arrayLength)
		stream->pos = arrayLength;
	if (stream->pos < 0)
		stream->pos = 0;

	stream->rp = stream->bp;
	stream->wp = stream->bp;
}

JNIEXPORT int JNICALL
JNI_FN(MuPDF_countPagesInternal)(JNIEnv *env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);

	return fz_count_pages(glo->doc);
}

JNIEXPORT void JNICALL
JNI_FN(MuPDF_gotoPageInternal)(JNIEnv *env, jobject thiz, int page)
{
	int i;
	int furthest;
	int furthest_dist = -1;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	page_cache *pc;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
		{
			/* The page is already cached */
			glo->current = i;
			return;
		}

		if (glo->pages[i].page == NULL)
		{
			/* cache record unused, and so a good one to use */
			furthest = i;
			furthest_dist = INT_MAX;
		}
		else
		{
			int dist = abs(glo->pages[i].number - page);

			/* Further away - less likely to be needed again */
			if (dist > furthest_dist)
			{
				furthest_dist = dist;
				furthest = i;
			}
		}
	}

	glo->current = furthest;
	pc = &glo->pages[glo->current];

	drop_page_cache(glo, pc);

	/* In the event of an error, ensure we give a non-empty page */
	pc->width = 100;
	pc->height = 100;

	pc->number = page;
	LOGE("Goto page %d...", page);
	fz_try(ctx)
	{
		fz_rect rect;
		LOGI("Load page %d", pc->number);
		pc->page = fz_load_page(glo->doc, pc->number);
		zoom = glo->resolution / 72;
		fz_bound_page(glo->doc, pc->page, &pc->media_box);
		fz_scale(&ctm, zoom, zoom);
		rect = pc->media_box;
		fz_round_rect(&bbox, fz_transform_rect(&rect, &ctm));
		pc->width = bbox.x1-bbox.x0;
		pc->height = bbox.y1-bbox.y0;
	}
	fz_catch(ctx)
	{
		LOGE("cannot make displaylist from page %d", pc->number);
	}
}

JNIEXPORT float JNICALL
JNI_FN(MuPDF_getPageWidth)(JNIEnv *env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	LOGE("PageWidth=%d", glo->pages[glo->current].width);
	return glo->pages[glo->current].width;
}

JNIEXPORT float JNICALL
JNI_FN(MuPDF_getPageHeight)(JNIEnv *env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);
	LOGE("PageHeight=%d", glo->pages[glo->current].height);
	return glo->pages[glo->current].height;
}

static void update_changed_rects(globals *glo, page_cache *pc, fz_interactive *idoc)
{
	fz_annot *annot;

	fz_update_page(idoc, pc->page);
	while ((annot = fz_poll_changed_annot(idoc, pc->page)) != NULL)
	{
		/* FIXME: We bound the annot twice here */
		rect_node *node = fz_malloc_struct(glo->ctx, rect_node);
		fz_bound_annot(glo->doc, annot, &node->rect);
		node->next = pc->changed_rects;
		pc->changed_rects = node;

		node = fz_malloc_struct(glo->ctx, rect_node);
		fz_bound_annot(glo->doc, annot, &node->rect);
		node->next = pc->hq_changed_rects;
		pc->hq_changed_rects = node;
	}
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDF_drawPage)(JNIEnv *env, jobject thiz, jobject bitmap,
		int pageW, int pageH, int patchX, int patchY, int patchW, int patchH)
{
	AndroidBitmapInfo info;
	void *pixels;
	int ret;
	fz_device *dev = NULL;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	fz_rect rect;
	fz_pixmap *pix = NULL;
	float xscale, yscale;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	page_cache *pc = &glo->pages[glo->current];
	int hq = (patchW < pageW || patchH < pageH);
	fz_matrix scale;

	if (pc->page == NULL)
		return 0;

	fz_var(pix);
	fz_var(dev);

	LOGI("In native method\n");
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	LOGI("Checking format\n");
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	LOGI("locking pixels\n");
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	/* Call mupdf to render display list to screen */
	LOGE("Rendering page(%d)=%dx%d patch=[%d,%d,%d,%d]",
			pc->number, pageW, pageH, patchX, patchY, patchW, patchH);

	fz_try(ctx)
	{
		fz_interactive *idoc = fz_interact(doc);

		if (idoc)
		{
			/* Update the changed-rects for both hq patch and main bitmap */
			update_changed_rects(glo, pc, idoc);

			/* Then drop the changed-rects for the bitmap we're about to
			render because we are rendering the entire area */
			drop_changed_rects(ctx, hq ? &pc->hq_changed_rects : &pc->changed_rects);
		}

		if (pc->page_list == NULL)
		{
			/* Render to list */
			pc->page_list = fz_new_display_list(ctx);
			dev = fz_new_list_device(ctx, pc->page_list);
			fz_run_page_contents(doc, pc->page, dev, &fz_identity, NULL);
		}
		if (pc->annot_list == NULL)
		{
			fz_annot *annot;
			if (dev)
			{
				fz_free_device(dev);
				dev = NULL;
			}
			pc->annot_list = fz_new_display_list(ctx);
			dev = fz_new_list_device(ctx, pc->annot_list);
			for (annot = fz_first_annot(doc, pc->page); annot; annot = fz_next_annot(doc, annot))
				fz_run_annot(doc, pc->page, annot, dev, &fz_identity, NULL);
		}
		bbox.x0 = patchX;
		bbox.y0 = patchY;
		bbox.x1 = patchX + patchW;
		bbox.y1 = patchY + patchH;
		pix = fz_new_pixmap_with_bbox_and_data(ctx, glo->colorspace, &bbox, pixels);
		if (pc->page_list == NULL && pc->annot_list == NULL)
		{
			fz_clear_pixmap_with_value(ctx, pix, 0xd0);
			break;
		}
		fz_clear_pixmap_with_value(ctx, pix, 0xff);

		zoom = glo->resolution / 72;
		fz_scale(&ctm, zoom, zoom);
		rect = pc->media_box;
		fz_round_rect(&bbox, fz_transform_rect(&rect, &ctm));
		/* Now, adjust ctm so that it would give the correct page width
		 * heights. */
		xscale = (float)pageW/(float)(bbox.x1-bbox.x0);
		yscale = (float)pageH/(float)(bbox.y1-bbox.y0);
		fz_concat(&ctm, &ctm, fz_scale(&scale, xscale, yscale));
		rect = pc->media_box;
		fz_transform_rect(&rect, &ctm);
		dev = fz_new_draw_device(ctx, pix);
#ifdef TIME_DISPLAY_LIST
		{
			clock_t time;
			int i;

			LOGE("Executing display list");
			time = clock();
			for (i=0; i<100;i++) {
#endif
				if (pc->page_list)
					fz_run_display_list(pc->page_list, dev, &ctm, &rect, NULL);
				if (pc->annot_list)
					fz_run_display_list(pc->annot_list, dev, &ctm, &rect, NULL);
#ifdef TIME_DISPLAY_LIST
			}
			time = clock() - time;
			LOGE("100 renders in %d (%d per sec)", time, CLOCKS_PER_SEC);
		}
#endif
		fz_free_device(dev);
		dev = NULL;
		fz_drop_pixmap(ctx, pix);
		LOGE("Rendered");
	}
	fz_catch(ctx)
	{
		fz_free_device(dev);
		LOGE("Render failed");
	}

	AndroidBitmap_unlockPixels(env, bitmap);

	return 1;
}

static char *widget_type_string(int t)
{
	switch(t)
	{
	case FZ_WIDGET_TYPE_PUSHBUTTON: return "pushbutton";
	case FZ_WIDGET_TYPE_CHECKBOX: return "checkbox";
	case FZ_WIDGET_TYPE_RADIOBUTTON: return "radiobutton";
	case FZ_WIDGET_TYPE_TEXT: return "text";
	case FZ_WIDGET_TYPE_LISTBOX: return "listbox";
	case FZ_WIDGET_TYPE_COMBOBOX: return "combobox";
	default: return "non-widget";
	}
}
JNIEXPORT jboolean JNICALL
JNI_FN(MuPDF_updatePageInternal)(JNIEnv *env, jobject thiz, jobject bitmap, int page,
		int pageW, int pageH, int patchX, int patchY, int patchW, int patchH)
{
	AndroidBitmapInfo info;
	void *pixels;
	int ret;
	fz_device *dev = NULL;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	fz_rect rect;
	fz_pixmap *pix = NULL;
	float xscale, yscale;
	fz_interactive *idoc;
	page_cache *pc = NULL;
	int hq = (patchW < pageW || patchH < pageH);
	int i;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	rect_node *crect;
	fz_matrix scale;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
		{
			pc = &glo->pages[i];
			break;
		}
	}

	if (pc == NULL)
	{
		/* Without a cached page object we cannot perform a partial update so
		render the entire bitmap instead */
		JNI_FN(MuPDF_gotoPageInternal)(env, thiz, page);
		return JNI_FN(MuPDF_drawPage)(env, thiz, bitmap, pageW, pageH, patchX, patchY, patchW, patchH);
	}

	idoc = fz_interact(doc);

	fz_var(pix);
	fz_var(dev);

	LOGI("In native method\n");
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	LOGI("Checking format\n");
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	LOGI("locking pixels\n");
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	/* Call mupdf to render display list to screen */
	LOGE("Rendering page(%d)=%dx%d patch=[%d,%d,%d,%d]",
			pc->number, pageW, pageH, patchX, patchY, patchW, patchH);

	fz_try(ctx)
	{
		fz_annot *annot;

		if (idoc)
		{
			/* Update the changed-rects for both hq patch and main bitmap */
			update_changed_rects(glo, pc, idoc);
		}

		if (pc->page_list == NULL)
		{
			/* Render to list */
			pc->page_list = fz_new_display_list(ctx);
			dev = fz_new_list_device(ctx, pc->page_list);
			fz_run_page_contents(doc, pc->page, dev, &fz_identity, NULL);
		}

		if (pc->annot_list == NULL) {
			if (dev) {
				fz_free_device(dev);
				dev = NULL;
			}
			pc->annot_list = fz_new_display_list(ctx);
			dev = fz_new_list_device(ctx, pc->annot_list);
			for (annot = fz_first_annot(doc, pc->page); annot; annot = fz_next_annot(doc, annot))
				fz_run_annot(doc, pc->page, annot, dev, &fz_identity, NULL);
		}

		bbox.x0 = patchX;
		bbox.y0 = patchY;
		bbox.x1 = patchX + patchW;
		bbox.y1 = patchY + patchH;
		pix = fz_new_pixmap_with_bbox_and_data(ctx, glo->colorspace, &bbox, pixels);

		zoom = glo->resolution / 72;
		fz_scale(&ctm, zoom, zoom);
		rect = pc->media_box;
		fz_round_rect(&bbox, fz_transform_rect(&rect, &ctm));
		/* Now, adjust ctm so that it would give the correct page width
		 * heights. */
		xscale = (float)pageW/(float)(bbox.x1-bbox.x0);
		yscale = (float)pageH/(float)(bbox.y1-bbox.y0);
		fz_concat(&ctm, &ctm, fz_scale(&scale, xscale, yscale));
		rect = pc->media_box;
		fz_transform_rect(&rect, &ctm);

		LOGI("Start partial update");
		for (crect = hq ? pc->hq_changed_rects : pc->changed_rects; crect; crect = crect->next)
		{
			fz_irect abox;
			fz_rect arect = crect->rect;
			fz_intersect_rect(fz_transform_rect(&arect, &ctm), &rect);
			fz_round_rect(&abox, &arect);

			LOGI("Update rectangle (%d, %d, %d, %d)", abox.x0, abox.y0, abox.x1, abox.y1);
			if (!fz_is_empty_irect(&abox))
			{
				LOGI("And it isn't empty");
				fz_clear_pixmap_rect_with_value(ctx, pix, 0xff, &abox);
				dev = fz_new_draw_device_with_bbox(ctx, pix, &abox);
				if (pc->page_list)
					fz_run_display_list(pc->page_list, dev, &ctm, &arect, NULL);
				if (pc->annot_list)
					fz_run_display_list(pc->annot_list, dev, &ctm, &arect, NULL);
				fz_free_device(dev);
				dev = NULL;
			}
		}
		LOGI("End partial update");

		/* Drop the changed rects we've just rendered */
		drop_changed_rects(ctx, hq ? &pc->hq_changed_rects : &pc->changed_rects);

		LOGE("Rendered");
	}
	fz_catch(ctx)
	{
		fz_free_device(dev);
		LOGE("Render failed");
	}

	fz_drop_pixmap(ctx, pix);
	AndroidBitmap_unlockPixels(env, bitmap);

	return 1;
}

static fz_text_char textcharat(fz_text_page *page, int idx)
{
	static fz_text_char emptychar = { {0,0,0,0}, ' ' };
	fz_text_block *block;
	fz_text_line *line;
	fz_text_span *span;
	int ofs = 0;
	for (block = page->blocks; block < page->blocks + page->len; block++)
	{
		for (line = block->lines; line < block->lines + block->len; line++)
		{
			for (span = line->spans; span < line->spans + line->len; span++)
			{
				if (idx < ofs + span->len)
					return span->text[idx - ofs];
				/* pseudo-newline */
				if (span + 1 == line->spans + line->len)
				{
					if (idx == ofs + span->len)
						return emptychar;
					ofs++;
				}
				ofs += span->len;
			}
		}
	}
	return emptychar;
}

static int
charat(fz_text_page *page, int idx)
{
	return textcharat(page, idx).c;
}

static fz_rect
bboxcharat(fz_text_page *page, int idx)
{
	return textcharat(page, idx).bbox;
}

static int
textlen(fz_text_page *page)
{
	fz_text_block *block;
	fz_text_line *line;
	fz_text_span *span;
	int len = 0;
	for (block = page->blocks; block < page->blocks + page->len; block++)
	{
		for (line = block->lines; line < block->lines + block->len; line++)
		{
			for (span = line->spans; span < line->spans + line->len; span++)
				len += span->len;
			len++; /* pseudo-newline */
		}
	}
	return len;
}

static int
match(fz_text_page *page, const char *s, int n)
{
	int orig = n;
	int c;
	while (*s) {
		s += fz_chartorune(&c, (char *)s);
		if (c == ' ' && charat(page, n) == ' ') {
			while (charat(page, n) == ' ')
				n++;
		} else {
			if (tolower(c) != tolower(charat(page, n)))
				return 0;
			n++;
		}
	}
	return n - orig;
}

static int
countOutlineItems(fz_outline *outline)
{
	int count = 0;

	while (outline)
	{
		if (outline->dest.kind == FZ_LINK_GOTO
				&& outline->dest.ld.gotor.page >= 0
				&& outline->title)
			count++;

		count += countOutlineItems(outline->down);
		outline = outline->next;
	}

	return count;
}

static int
fillInOutlineItems(JNIEnv * env, jclass olClass, jmethodID ctor, jobjectArray arr, int pos, fz_outline *outline, int level)
{
	while (outline)
	{
		if (outline->dest.kind == FZ_LINK_GOTO)
		{
			int page = outline->dest.ld.gotor.page;
			if (page >= 0 && outline->title)
			{
				jobject ol;
				jstring title = (*env)->NewStringUTF(env, outline->title);
				if (title == NULL) return -1;
				ol = (*env)->NewObject(env, olClass, ctor, level, title, page);
				if (ol == NULL) return -1;
				(*env)->SetObjectArrayElement(env, arr, pos, ol);
				(*env)->DeleteLocalRef(env, ol);
				(*env)->DeleteLocalRef(env, title);
				pos++;
			}
		}
		pos = fillInOutlineItems(env, olClass, ctor, arr, pos, outline->down, level+1);
		if (pos < 0) return -1;
		outline = outline->next;
	}

	return pos;
}

static void close_doc(globals *glo)
{
	int i;

	fz_free(glo->ctx, glo->hit_bbox);
	glo->hit_bbox = NULL;

	for (i = 0; i < NUM_CACHE; i++)
		drop_page_cache(glo, &glo->pages[i]);

	alerts_fin(glo);

	fz_close_document(glo->doc);
	glo->doc = NULL;
}

JNIEXPORT void JNICALL
JNI_FN(MuPDF_destroying)(JNIEnv * env, jobject thiz)
{
	globals *glo = get_globals(env, thiz);

	LOGI("Destroying");
	close_doc(glo);
	fz_free(glo->ctx, glo->current_path);
	glo->current_path = NULL;
	free(glo);

#ifdef NDK_PROFILER
	// Apparently we should really be writing to whatever path we get
	// from calling getFilesDir() in the java part, which supposedly
	// gives /sdcard/data/data/com.artifex.MuPDF/gmon.out, but that's
	// unfriendly.
	setenv("CPUPROFILE", "/sdcard/gmon.out", 1);
	moncleanup();
#endif
}

static char *tmp_path(char *path)
{
	int f;
	char *buf = malloc(strlen(path) + 6 + 1);
	if (!buf)
		return NULL;

	strcpy(buf, path);
	strcat(buf, "XXXXXX");

	f = mkstemp(buf);

	if (f >= 0)
	{
		close(f);
		return buf;
	}
	else
	{
		free(buf);
		return NULL;
	}
}
