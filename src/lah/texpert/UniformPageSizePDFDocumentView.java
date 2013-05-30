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

	/* Shim function */
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

	public synchronized Bitmap updatePage(BitmapHolder h, int page, int pageW, int pageH, int patchX, int patchY,
			int patchW, int patchH) {
		Bitmap bm = null;
		Bitmap old_bm = h.getBm();

		if (old_bm == null)
			return null;

		bm = old_bm.copy(Bitmap.Config.ARGB_8888, false);
		old_bm = null;

		updatePageInternal(bm, page, pageW, pageH, patchX, patchY, patchW, patchH);
		return bm;
	}

	private native void updatePageInternal(Bitmap bitmap, int page, int pageW, int pageH, int patchX, int patchY,
			int patchW, int patchH);

}

/**
 * Class is copied verbatim from MuPDF
 */
class BitmapHolder {

	private Bitmap bm;

	public BitmapHolder() {
		bm = null;
	}

	public synchronized void drop() {
		bm = null;
	}

	public synchronized Bitmap getBm() {
		return bm;
	}

	public synchronized void setBm(Bitmap abm) {
		if (bm != null && bm != abm)
			bm.recycle();
		bm = abm;
	}

}

/**
 * A simple {@link View} to display PDF document. The document to be display must have <b>identical sizes for all
 * pages</b>. When the view is not in used, client should call {@link #release()} to free the resources.
 * 
 * @author L.A.H.
 * 
 */
public class UniformPageSizePDFDocumentView extends AbstractZoomableScrollView {

	private static final boolean DEBUG = false;

	private static final Matrix IDENTITY_MATRIX = new Matrix();

	private MuPDF mupdf;

	private int num_pages;

	private float page_width = -1, page_height = -1;

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
		super.onDraw(canvas);
		if (mupdf == null || page_width <= 0 || page_height <= 0)
			return;
		// determine visible pages and their visible regions
		float zf = 2.0f;
		int w = getWidth(), h = getHeight();
		int px = (int) (getScrollX() * zf), py = (int) (getScrollY() * zf);
		if (DEBUG)
			Log.v("PdfView", "Scroll state : [" + px + " " + py + "]");
		// render the pages into the canvas
		canvas.drawBitmap(mupdf.drawPage(0, (int) (page_width * zf), (int) (page_height * zf), px, py, w, h),
				IDENTITY_MATRIX, null);
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
			mh = page_height > 0 ? (int) (page_height * num_pages) : MeasureSpec.getSize(heightMeasureSpec);
			break;
		default:
			mh = MeasureSpec.getSize(heightMeasureSpec);
		}
		// Log.v("onMeasure",
		// "Measured size for spec [" + MeasureSpec.toString(widthMeasureSpec) + ","
		// + MeasureSpec.toString(heightMeasureSpec) + "] = " + mw + "x" + mh);
		setMeasuredDimension(mw, mh);
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		Log.v("PDFView", "onScrollChanged[" + l + "," + t + "," + oldl + "," + oldt);
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