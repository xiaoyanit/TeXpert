package lah.texpert;

import lah.widgets.AbstractZoomableScrollView;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

/**
 * A modified version of MuPDFCore class: class name is simplified to MuPDF, several methods are dropped.
 * 
 * @author L.A.H.
 * @date 28 May 2013
 * 
 */
class MuPDF {

	/* load our native library */
	static {
		System.loadLibrary("mupdf");
	}

	private long globals;

	private int numPages = -1;

	private float pageWidth, pageHeight;

	public MuPDF(String filename) throws Exception {
		globals = openFile(filename);
		if (globals == 0) {
			throw new Exception("Failed to open " + filename);
		}
	}

	public int countPages() {
		if (numPages < 0)
			numPages = countPagesSynchronized();

		return numPages;
	}

	private native int countPagesInternal();

	private synchronized int countPagesSynchronized() {
		return countPagesInternal();
	}

	private native void destroying();

	private native void drawPage(Bitmap bitmap, int pageW, int pageH, int patchX, int patchY, int patchW, int patchH);

	public synchronized Bitmap drawPage(int page, int pageW, int pageH, int patchX, int patchY, int patchW, int patchH) {
		gotoPage(page);
		Bitmap bm = Bitmap.createBitmap(patchW, patchH, Config.ARGB_8888);
		drawPage(bm, pageW, pageH, patchX, patchY, patchW, patchH);
		return bm;
	}

	private native float getPageHeight();

	public synchronized PointF getPageSize(int page) {
		gotoPage(page);
		return new PointF(pageWidth, pageHeight);
	}

	private native float getPageWidth();

	private void gotoPage(int page) {
		if (page > numPages - 1)
			page = numPages - 1;
		else if (page < 0)
			page = 0;
		gotoPageInternal(page);
		this.pageWidth = getPageWidth();
		this.pageHeight = getPageHeight();
	}

	private native void gotoPageInternal(int localActionPageNum);

	public synchronized void onDestroy() {
		destroying();
		globals = 0;
	}

	private native long openFile(String filename);

}

/**
 * A simple {@link View} to display PDF document. The document to be display must have <b>identical sizes for all
 * pages</b>. When the view is not in used, client should call {@link #release()} to free the resources. On the other
 * hand, it is probably preferable to hide the soft input method when this view is shown.
 * 
 * @author L.A.H.
 * 
 */
public class UniformPageSizePDFDocumentView extends AbstractZoomableScrollView {

	private static final boolean DEBUG = false;

	private static final Matrix IDENTITY_MATRIX = new Matrix();

	static final float PAGE_GAP = 20;

	private static final String TAG = "Uniform Page Size PDF Document View";

	private MuPDF mupdf;

	private int num_pages;

	private float page_width = -1, page_height = -1, total_page_height_with_gap = -1;

	public UniformPageSizePDFDocumentView(Context context) {
		super(context);
	}

	public UniformPageSizePDFDocumentView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public UniformPageSizePDFDocumentView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public boolean isOpaque() {
		return true;
	}

	@Override
	protected synchronized void onDraw(Canvas canvas) {
		// No PDF file to display or page metric not available
		if (mupdf == null || page_width <= 0 || page_height <= 0)
			return;

		// Determine visible pages and their visible regions
		int viewport_width = getWidth();
		int viewport_height = getHeight();
		float zf = getZoomFactor();
		int zoomed_page_width = (int) (page_width * zf);
		int zoomed_page_height = (int) (page_height * zf);

		// Restrict the position of the viewport
		// TODO Allow for infinite-size navigation area
		float max_viewport_x = page_width - viewport_width / zf;
		float max_viewport_y = total_page_height_with_gap - (viewport_height / zf);
		setViewportX(Math.min(max_viewport_x, getViewportX()));
		setViewportY(Math.min(max_viewport_y, getViewportY()));

		// Determine page containing the point (vpx, vpy)
		float page_height_with_gap = page_height + PAGE_GAP;
		int start_page = (int) (getViewportY() / page_height_with_gap);
		if (DEBUG) {
			Log.v(TAG, "Viewport: [" + viewport_X + "," + viewport_Y + "]");
			Log.v(TAG, "First page to display = " + start_page);
		}
		if (start_page >= num_pages)
			return;

		// Get the patch area for the page containing viewport corner
		int patch_width = (int) Math.min(viewport_width, (page_width - getViewportX()) * zf);
		if (patch_width <= 0)
			return;

		int patch_x = (int) (getViewportX() * zf);
		int patch_y = (int) ((getViewportY() - start_page * page_height_with_gap) * zf);
		int patch_height = (int) Math.min(viewport_height,
				(start_page * page_height_with_gap + page_height - getViewportY()) * zf);

		// Paint consecutive pages until viewport is filled up or no more page
		float rem_height = viewport_height;
		canvas.save();
		// TODO Take care of top/left paddings as well
		// canvas.translate(getLeft(), getTop());
		while (patch_height > 0 && start_page < num_pages) {
			// Draw the page patch
			if (DEBUG)
				Log.v(TAG, "Draw patch [" + patch_x + "," + patch_y + "," + patch_width + "," + patch_height
						+ "] of page " + start_page);
			canvas.drawBitmap(mupdf.drawPage(start_page, zoomed_page_width, zoomed_page_height, patch_x, patch_y,
					patch_width, patch_height), IDENTITY_MATRIX, null);

			// Translate the canvas to draw next page
			canvas.translate(0, patch_height + PAGE_GAP * zf);

			start_page++;

			// Update drawing metric for the next page
			rem_height -= (patch_height + PAGE_GAP * zf);
			if (DEBUG)
				Log.v(TAG, "Remaining height = " + rem_height);

			// After the first page is drawn, subsequent pages all start from top and goes as much as it can, up to
			// remaining space and its height; all pages shared the same patch_x and patch_width though.
			patch_y = 0;
			patch_height = (int) Math.min(rem_height, zoomed_page_height);
		}
		canvas.restore();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (mupdf == null)
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int mw, mh;
		switch (MeasureSpec.getMode(widthMeasureSpec)) {
		case View.MeasureSpec.UNSPECIFIED:
			mw = page_width > 0 ? (int) page_width : MeasureSpec.getSize(widthMeasureSpec);
			break;
		default:
			mw = MeasureSpec.getSize(widthMeasureSpec);
		}
		switch (MeasureSpec.getMode(heightMeasureSpec)) {
		case MeasureSpec.UNSPECIFIED:
			mh = page_height > 0 ? (int) (total_page_height_with_gap) : MeasureSpec
					.getSize(heightMeasureSpec);
			break;
		default:
			mh = MeasureSpec.getSize(heightMeasureSpec);
		}
		if (DEBUG)
			Log.v(TAG,
					"Measured dimensions for spec [" + MeasureSpec.toString(widthMeasureSpec) + ","
							+ MeasureSpec.toString(heightMeasureSpec) + "] = " + mw + "x" + mh);
		setMeasuredDimension(mw, mh);
	}

	public void release() {
		if (mupdf != null)
			mupdf.onDestroy();
		mupdf = null;
	}

	public void setDisplayPDF(String path_to_pdf) {
		release();
		try {
			mupdf = new MuPDF(path_to_pdf);
		} catch (Exception e) {
			e.printStackTrace(System.out);
			mupdf = null;
			Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
			return;
		}

		if ((num_pages = mupdf.countPages()) == 0)
			return;

		// Compute the size of rectangle to display the whole document
		// TODO Do this in background thread and postInvalidate when done
		page_width = page_height = -1;
		PointF page_size = mupdf.getPageSize(0);
		page_width = page_size.x;
		page_height = page_size.y;
		total_page_height_with_gap = page_height * num_pages + PAGE_GAP * (num_pages - 1);

		// The following code is for general case
		// page_sizes = new PointF[mupdf.countPages()];
		// float max_page_width = 0, total_doc_height = 0;
		// for (int i = 0; i < mupdf.countPages(); i++) {
		// page_sizes[i] = mupdf.getPageSize(i);
		// max_page_width = Math.max(max_page_width, page_sizes[i].x);
		// total_doc_height += page_sizes[i].y;
		// }

		postInvalidate();
	}

}
