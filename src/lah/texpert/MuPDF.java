/*
 * This file is derived from MuPDF Android app's source code by packaging a minimal amount of code for the purpose of TeXpert.
 * Major changes are:
 *  - The name of class MuPDFCore is simplified to MuPDF.
 *  - Remove methods and fields related to text selection, editing, links, script, etc.
 * 
 * @author L.A.H.
 * @modified-date 28 May 2013
 */
package lah.texpert;

import java.util.LinkedList;
import java.util.NoSuchElementException;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Scroller;

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

public class MuPDF {

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
 * Copied from MuPDF source code
 */
class MuPDFPageAdapter extends BaseAdapter {

	private final Context mContext;

	private final MuPDF mCore;

	private final SparseArray<PointF> mPageSizes = new SparseArray<PointF>();

	public MuPDFPageAdapter(Context c, MuPDF core) {
		mContext = c;
		mCore = core;
	}

	public int getCount() {
		return mCore.countPages();
	}

	public Object getItem(int position) {
		return null;
	}

	public long getItemId(int position) {
		return 0;
	}

	public View getView(final int position, View convertView, ViewGroup parent) {
		final MuPDFPageView pageView;
		if (convertView == null) {
			pageView = new MuPDFPageView(mContext, mCore, new Point(parent.getWidth(), parent.getHeight()));
		} else {
			pageView = (MuPDFPageView) convertView;
		}

		PointF pageSize = mPageSizes.get(position);
		if (pageSize != null) {
			// We already know the page size. Set it up
			// immediately
			pageView.setPage(position, pageSize);
		} else {
			// Page size as yet unknown. Blank it for now, and start a background task to find the size
			pageView.blank(position);
			AsyncTask<Void, Void, PointF> sizingTask = new AsyncTask<Void, Void, PointF>() {
				@Override
				protected PointF doInBackground(Void... arg0) {
					return mCore.getPageSize(position);
				}

				@Override
				protected void onPostExecute(PointF result) {
					super.onPostExecute(result);
					// We now know the page size
					mPageSizes.put(position, result);
					// Check that this view hasn't been reused for another page since we started
					if (pageView.getPage() == position)
						pageView.setPage(position, result);
				}
			};
			sizingTask.execute((Void) null);
		}
		return pageView;
	}
}

class MuPDFPageView extends ViewGroup {

	private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
	// private static final int HIGHLIGHT_COLOR = 0x802572AC;
	// private static final int LINK_COLOR = 0x80AC7225;
	private static final int PROGRESS_DIALOG_DELAY = 200;
	private ProgressBar mBusyIndicator;
	// private static final float LINE_THICKNESS = 0.07f;
	// private static final float STRIKE_HEIGHT = 0.375f;
	private final Context mContext;
	private final MuPDF mCore;
	private AsyncTask<Void, Void, Bitmap> mDrawEntire;
	private AsyncTask<PatchInfo, Void, PatchInfo> mDrawPatch;

	private ImageView mEntire; // Image rendered at minimum zoom
	private BitmapHolder mEntireBmh;
	private final Handler mHandler = new Handler();
	// private boolean mHighlightLinks;
	// private boolean mIsBlank;
	protected int mPageNumber;
	private Point mParentSize;
	private ImageView mPatch;
	private Rect mPatchArea;
	private BitmapHolder mPatchBmh;
	private Point mPatchViewSize; // View size on the basis of which the patch was created
	protected Point mSize; // Size of page at minimum zoom
	protected float mSourceScale;

	public MuPDFPageView(Context c, MuPDF core, Point parentSize) {
		super(c);
		mContext = c;
		mParentSize = parentSize;
		setBackgroundColor(BACKGROUND_COLOR);
		mEntireBmh = new BitmapHolder();
		mPatchBmh = new BitmapHolder();
		mCore = core;
	}

	public void addHq(boolean update) {
		Rect viewArea = new Rect(getLeft(), getTop(), getRight(), getBottom());
		// If the viewArea's size matches the unzoomed size, there is no need for an hq patch
		if (viewArea.width() != mSize.x || viewArea.height() != mSize.y) {
			Point patchViewSize = new Point(viewArea.width(), viewArea.height());
			Rect patchArea = new Rect(0, 0, mParentSize.x, mParentSize.y);

			// Intersect and test that there is an intersection
			if (!patchArea.intersect(viewArea))
				return;

			// Offset patch area to be relative to the view top left
			patchArea.offset(-viewArea.left, -viewArea.top);

			boolean area_unchanged = patchArea.equals(mPatchArea) && patchViewSize.equals(mPatchViewSize);

			// If being asked for the same area as last time and not because of an update then nothing to do
			if (area_unchanged && !update)
				return;

			boolean completeRedraw = !(area_unchanged && update);

			// Stop the drawing of previous patch if still going
			if (mDrawPatch != null) {
				mDrawPatch.cancel(true);
				mDrawPatch = null;
			}

			if (completeRedraw) {
				// The bitmap holder mPatchBm may still be rendered to by a
				// previously invoked task, and possibly for a different
				// area, so we cannot risk the bitmap generated by this task
				// being passed to it
				mPatchBmh.drop();
				mPatchBmh = new BitmapHolder();
			}

			// Create and add the image view if not already done
			if (mPatch == null) {
				mPatch = new OpaqueImageView(mContext);
				mPatch.setScaleType(ImageView.ScaleType.FIT_CENTER);
				addView(mPatch);
				// mSearchView.bringToFront();
			}

			mDrawPatch = new AsyncTask<PatchInfo, Void, PatchInfo>() {
				protected PatchInfo doInBackground(PatchInfo... v) {
					if (v[0].completeRedraw) {
						v[0].bm = drawPage(v[0].patchViewSize.x, v[0].patchViewSize.y, v[0].patchArea.left,
								v[0].patchArea.top, v[0].patchArea.width(), v[0].patchArea.height());
					} else {
						v[0].bm = updatePage(v[0].bmh, v[0].patchViewSize.x, v[0].patchViewSize.y, v[0].patchArea.left,
								v[0].patchArea.top, v[0].patchArea.width(), v[0].patchArea.height());
					}

					return v[0];
				}

				protected void onPostExecute(PatchInfo v) {
					if (mPatchBmh == v.bmh) {
						mPatchViewSize = v.patchViewSize;
						mPatchArea = v.patchArea;
						if (v.bm != null) {
							mPatch.setImageBitmap(v.bm);
							v.bmh.setBm(v.bm);
							v.bm = null;
						}
						// requestLayout();
						// Calling requestLayout here doesn't lead to a later call to layout. No idea
						// why, but apparently others have run into the problem.
						mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
						invalidate();
					}
				}
			};

			mDrawPatch.execute(new PatchInfo(patchViewSize, patchArea, mPatchBmh, completeRedraw));
		}
	}

	public void blank(int page) {
		reinit();
		mPageNumber = page;

		if (mBusyIndicator == null) {
			mBusyIndicator = new ProgressBar(mContext);
			mBusyIndicator.setIndeterminate(true);
			addView(mBusyIndicator);
		}
	}

	protected Bitmap drawPage(int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight) {
		return mCore.drawPage(mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight);
	}

	public int getPage() {
		return mPageNumber;
	}

	@Override
	public boolean isOpaque() {
		return true;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		int w = right - left;
		int h = bottom - top;

		if (mEntire != null) {
			mEntire.layout(0, 0, w, h);
		}

		if (mPatchViewSize != null) {
			if (mPatchViewSize.x != w || mPatchViewSize.y != h) {
				// Zoomed since patch was created
				mPatchViewSize = null;
				mPatchArea = null;
				if (mPatch != null) {
					mPatch.setImageBitmap(null);
					mPatchBmh.setBm(null);
				}
			} else {
				mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
			}
		}

		if (mBusyIndicator != null) {
			int bw = mBusyIndicator.getMeasuredWidth();
			int bh = mBusyIndicator.getMeasuredHeight();

			mBusyIndicator.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int x, y;
		switch (View.MeasureSpec.getMode(widthMeasureSpec)) {
		case View.MeasureSpec.UNSPECIFIED:
			x = mSize.x;
			break;
		default:
			x = View.MeasureSpec.getSize(widthMeasureSpec);
		}
		switch (View.MeasureSpec.getMode(heightMeasureSpec)) {
		case View.MeasureSpec.UNSPECIFIED:
			y = mSize.y;
			break;
		default:
			y = View.MeasureSpec.getSize(heightMeasureSpec);
		}

		setMeasuredDimension(x, y);

		if (mBusyIndicator != null) {
			int limit = Math.min(mParentSize.x, mParentSize.y) / 2;
			mBusyIndicator.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
		}
	}

	private void reinit() {
		// Cancel pending render task
		if (mDrawEntire != null) {
			mDrawEntire.cancel(true);
			mDrawEntire = null;
		}

		if (mDrawPatch != null) {
			mDrawPatch.cancel(true);
			mDrawPatch = null;
		}

		// mIsBlank = true;
		mPageNumber = 0;

		if (mSize == null)
			mSize = mParentSize;

		if (mEntire != null) {
			mEntire.setImageBitmap(null);
			mEntireBmh.setBm(null);
		}

		if (mPatch != null) {
			mPatch.setImageBitmap(null);
			mPatchBmh.setBm(null);
		}

		mPatchViewSize = null;
		mPatchArea = null;
	}

	public void releaseResources() {
		reinit();

		if (mBusyIndicator != null) {
			removeView(mBusyIndicator);
			mBusyIndicator = null;
		}
	}

	public void removeHq() {
		// Stop the drawing of the patch if still going
		if (mDrawPatch != null) {
			mDrawPatch.cancel(true);
			mDrawPatch = null;
		}

		// And get rid of it
		mPatchViewSize = null;
		mPatchArea = null;
		if (mPatch != null) {
			mPatch.setImageBitmap(null);
			mPatchBmh.setBm(null);
		}
	}

	public void setPage(int page, PointF size) {
		// Cancel pending render task
		if (mDrawEntire != null) {
			mDrawEntire.cancel(true);
			mDrawEntire = null;
		}

		// mIsBlank = false;

		mPageNumber = page;
		if (mEntire == null) {
			mEntire = new OpaqueImageView(mContext);
			mEntire.setScaleType(ImageView.ScaleType.FIT_CENTER);
			addView(mEntire);
		}

		// Calculate scaled size that fits within the screen limits
		// This is the size at minimum zoom
		mSourceScale = Math.min(mParentSize.x / size.x, mParentSize.y / size.y);
		Point newSize = new Point((int) (size.x * mSourceScale), (int) (size.y * mSourceScale));
		mSize = newSize;

		mEntire.setImageBitmap(null);
		mEntireBmh.setBm(null);

		// Render the page in the background
		mDrawEntire = new AsyncTask<Void, Void, Bitmap>() {
			protected Bitmap doInBackground(Void... v) {
				return drawPage(mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
			}

			protected void onPostExecute(Bitmap bm) {
				removeView(mBusyIndicator);
				mBusyIndicator = null;
				mEntire.setImageBitmap(bm);
				mEntireBmh.setBm(bm);
				invalidate();
			}

			protected void onPreExecute() {
				mEntire.setImageBitmap(null);
				mEntireBmh.setBm(null);

				if (mBusyIndicator == null) {
					mBusyIndicator = new ProgressBar(mContext);
					mBusyIndicator.setIndeterminate(true);
					addView(mBusyIndicator);
					mBusyIndicator.setVisibility(INVISIBLE);
					mHandler.postDelayed(new Runnable() {
						public void run() {
							if (mBusyIndicator != null)
								mBusyIndicator.setVisibility(VISIBLE);
						}
					}, PROGRESS_DIALOG_DELAY);
				}
			}
		};

		mDrawEntire.execute();
		requestLayout();
	}

	public void setScale(float scale) {
		// This type of view scales automatically to fit the size determined by the parent view groups during layout
	}

	public void update() {
		// Cancel pending render task
		if (mDrawEntire != null) {
			mDrawEntire.cancel(true);
			mDrawEntire = null;
		}

		if (mDrawPatch != null) {
			mDrawPatch.cancel(true);
			mDrawPatch = null;
		}

		// Render the page in the background
		mDrawEntire = new AsyncTask<Void, Void, Bitmap>() {
			protected Bitmap doInBackground(Void... v) {
				// Pass the current bitmap as a basis for the update, but use a bitmap
				// holder so that the held bitmap will be nulled and not hold on to
				// memory, should this view become redundant.
				return updatePage(mEntireBmh, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
			}

			protected void onPostExecute(Bitmap bm) {
				if (bm != null) {
					mEntire.setImageBitmap(bm);
					mEntireBmh.setBm(bm);
				}
				invalidate();
			}
		};

		mDrawEntire.execute();

		addHq(true);
	}

	protected Bitmap updatePage(BitmapHolder h, int sizeX, int sizeY, int patchX, int patchY, int patchWidth,
			int patchHeight) {
		return mCore.updatePage(h, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight);
	}
}

// Make our ImageViews opaque to optimize redraw
class OpaqueImageView extends ImageView {

	public OpaqueImageView(Context context) {
		super(context);
	}

	@Override
	public boolean isOpaque() {
		return true;
	}
}

class PatchInfo {
	public Bitmap bm;
	public BitmapHolder bmh;
	public boolean completeRedraw;
	public Rect patchArea;
	public Point patchViewSize;

	public PatchInfo(Point aPatchViewSize, Rect aPatchArea, BitmapHolder aBmh, boolean aCompleteRedraw) {
		bmh = aBmh;
		bm = null;
		patchViewSize = aPatchViewSize;
		patchArea = aPatchArea;
		completeRedraw = aCompleteRedraw;
	}
}

class ReaderView extends AdapterView<Adapter> implements GestureDetector.OnGestureListener,
		ScaleGestureDetector.OnScaleGestureListener, Runnable {
	private static final int MOVING_DIAGONALLY = 0;
	private static final int MOVING_LEFT = 1;
	private static final int MOVING_RIGHT = 2;
	private static final int MOVING_UP = 3;
	private static final int MOVING_DOWN = 4;

	private static final int FLING_MARGIN = 100;
	private static final int GAP = 20;

	private static final float MIN_SCALE = 1.0f;
	private static final float MAX_SCALE = 5.0f;
	private static final float REFLOW_SCALE_FACTOR = 0.5f;

	private Adapter mAdapter;
	private int mCurrent; // Adapter's index for the current view
	private boolean mResetLayout;
	private final SparseArray<View> mChildViews = new SparseArray<View>(3);
	// Shadows the children of the adapter view
	// but with more sensible indexing
	private final LinkedList<View> mViewCache = new LinkedList<View>();
	private boolean mUserInteracting; // Whether the user is interacting
	private boolean mScaling; // Whether the user is currently pinch zooming
	private float mScale = 1.0f;
	private int mXScroll; // Scroll amounts recorded from events.
	private int mYScroll; // and then accounted for in onLayout
	private boolean mReflow = false;
	private final GestureDetector mGestureDetector;
	private final ScaleGestureDetector mScaleGestureDetector;
	private final Scroller mScroller;
	private int mScrollerLastX;
	private int mScrollerLastY;
	private boolean mScrollDisabled;

	static abstract class ViewMapper {
		abstract void applyToView(View view);
	}

	public ReaderView(Context context) {
		super(context);
		mGestureDetector = new GestureDetector(context, this);
		mScaleGestureDetector = new ScaleGestureDetector(context, this);
		mScroller = new Scroller(context);
	}

	public ReaderView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mGestureDetector = new GestureDetector(context, this);
		mScaleGestureDetector = new ScaleGestureDetector(context, this);
		mScroller = new Scroller(context);
	}

	public ReaderView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mGestureDetector = new GestureDetector(context, this);
		mScaleGestureDetector = new ScaleGestureDetector(context, this);
		mScroller = new Scroller(context);
	}

	public int getDisplayedViewIndex() {
		return mCurrent;
	}

	public void setDisplayedViewIndex(int i) {
		if (0 <= i && i < mAdapter.getCount()) {
			mCurrent = i;
			onMoveToChild(i);
			mResetLayout = true;
			requestLayout();
		}
	}

	public void moveToNext() {
		View v = mChildViews.get(mCurrent + 1);
		if (v != null)
			slideViewOntoScreen(v);
	}

	public void moveToPrevious() {
		View v = mChildViews.get(mCurrent - 1);
		if (v != null)
			slideViewOntoScreen(v);
	}

	// When advancing down the page, we want to advance by about
	// 90% of a screenful. But we'd be happy to advance by between
	// 80% and 95% if it means we hit the bottom in a whole number
	// of steps.
	private int smartAdvanceAmount(int screenHeight, int max) {
		int advance = (int) (screenHeight * 0.9 + 0.5);
		int leftOver = max % advance;
		int steps = max / advance;
		if (leftOver == 0) {
			// We'll make it exactly. No adjustment
		} else if ((float) leftOver / steps <= screenHeight * 0.05) {
			// We can adjust up by less than 5% to make it exact.
			advance += (int) ((float) leftOver / steps + 0.5);
		} else {
			int overshoot = advance - leftOver;
			if ((float) overshoot / steps <= screenHeight * 0.1) {
				// We can adjust down by less than 10% to make it exact.
				advance -= (int) ((float) overshoot / steps + 0.5);
			}
		}
		if (advance > max)
			advance = max;
		return advance;
	}

	public void smartMoveForwards() {
		View v = mChildViews.get(mCurrent);
		if (v == null)
			return;

		// The following code works in terms of where the screen is on the views;
		// so for example, if the currentView is at (-100,-100), the visible
		// region would be at (100,100). If the previous page was (2000, 3000) in
		// size, the visible region of the previous page might be (2100 + GAP, 100)
		// (i.e. off the previous page). This is different to the way the rest of
		// the code in this file is written, but it's easier for me to think about.
		// At some point we may refactor this to fit better with the rest of the
		// code.

		// screenWidth/Height are the actual width/height of the screen. e.g. 480/800
		int screenWidth = getWidth();
		int screenHeight = getHeight();
		// We might be mid scroll; we want to calculate where we scroll to based on
		// where this scroll would end, not where we are now (to allow for people
		// bashing 'forwards' very fast.
		int remainingX = mScroller.getFinalX() - mScroller.getCurrX();
		int remainingY = mScroller.getFinalY() - mScroller.getCurrY();
		// right/bottom is in terms of pixels within the scaled document; e.g. 1000
		int top = -(v.getTop() + mYScroll + remainingY);
		int right = screenWidth - (v.getLeft() + mXScroll + remainingX);
		int bottom = screenHeight + top;
		// docWidth/Height are the width/height of the scaled document e.g. 2000x3000
		int docWidth = v.getMeasuredWidth();
		int docHeight = v.getMeasuredHeight();

		int xOffset, yOffset;
		if (bottom >= docHeight) {
			// We are flush with the bottom. Advance to next column.
			if (right + screenWidth > docWidth) {
				// No room for another column - go to next page
				View nv = mChildViews.get(mCurrent + 1);
				if (nv == null) // No page to advance to
					return;
				int nextTop = -(nv.getTop() + mYScroll + remainingY);
				int nextLeft = -(nv.getLeft() + mXScroll + remainingX);
				int nextDocWidth = nv.getMeasuredWidth();
				int nextDocHeight = nv.getMeasuredHeight();

				// Allow for the next page maybe being shorter than the screen is high
				yOffset = (nextDocHeight < screenHeight ? ((nextDocHeight - screenHeight) >> 1) : 0);

				if (nextDocWidth < screenWidth) {
					// Next page is too narrow to fill the screen. Scroll to the top, centred.
					xOffset = (nextDocWidth - screenWidth) >> 1;
				} else {
					// Reset X back to the left hand column
					xOffset = right % screenWidth;
					// Adjust in case the previous page is less wide
					if (xOffset + screenWidth > nextDocWidth)
						xOffset = nextDocWidth - screenWidth;
				}
				xOffset -= nextLeft;
				yOffset -= nextTop;
			} else {
				// Move to top of next column
				xOffset = screenWidth;
				yOffset = screenHeight - bottom;
			}
		} else {
			// Advance by 90% of the screen height downwards (in case lines are partially cut off)
			xOffset = 0;
			yOffset = smartAdvanceAmount(screenHeight, docHeight - bottom);
		}
		mScrollerLastX = mScrollerLastY = 0;
		mScroller.startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
		post(this);
	}

	public void smartMoveBackwards() {
		View v = mChildViews.get(mCurrent);
		if (v == null)
			return;

		// The following code works in terms of where the screen is on the views;
		// so for example, if the currentView is at (-100,-100), the visible
		// region would be at (100,100). If the previous page was (2000, 3000) in
		// size, the visible region of the previous page might be (2100 + GAP, 100)
		// (i.e. off the previous page). This is different to the way the rest of
		// the code in this file is written, but it's easier for me to think about.
		// At some point we may refactor this to fit better with the rest of the
		// code.

		// screenWidth/Height are the actual width/height of the screen. e.g. 480/800
		int screenWidth = getWidth();
		int screenHeight = getHeight();
		// We might be mid scroll; we want to calculate where we scroll to based on
		// where this scroll would end, not where we are now (to allow for people
		// bashing 'forwards' very fast.
		int remainingX = mScroller.getFinalX() - mScroller.getCurrX();
		int remainingY = mScroller.getFinalY() - mScroller.getCurrY();
		// left/top is in terms of pixels within the scaled document; e.g. 1000
		int left = -(v.getLeft() + mXScroll + remainingX);
		int top = -(v.getTop() + mYScroll + remainingY);
		// docWidth/Height are the width/height of the scaled document e.g. 2000x3000
		@SuppressWarnings("unused")
		int docWidth = v.getMeasuredWidth();
		int docHeight = v.getMeasuredHeight();

		int xOffset, yOffset;
		if (top <= 0) {
			// We are flush with the top. Step back to previous column.
			if (left < screenWidth) {
				/* No room for previous column - go to previous page */
				View pv = mChildViews.get(mCurrent - 1);
				if (pv == null) /* No page to advance to */
					return;
				int prevDocWidth = pv.getMeasuredWidth();
				int prevDocHeight = pv.getMeasuredHeight();

				// Allow for the next page maybe being shorter than the screen is high
				yOffset = (prevDocHeight < screenHeight ? ((prevDocHeight - screenHeight) >> 1) : 0);

				int prevLeft = -(pv.getLeft() + mXScroll);
				int prevTop = -(pv.getTop() + mYScroll);
				if (prevDocWidth < screenWidth) {
					// Previous page is too narrow to fill the screen. Scroll to the bottom, centred.
					xOffset = (prevDocWidth - screenWidth) >> 1;
				} else {
					// Reset X back to the right hand column
					xOffset = (left > 0 ? left % screenWidth : 0);
					if (xOffset + screenWidth > prevDocWidth)
						xOffset = prevDocWidth - screenWidth;
					while (xOffset + screenWidth * 2 < prevDocWidth)
						xOffset += screenWidth;
				}
				xOffset -= prevLeft;
				yOffset -= prevTop - prevDocHeight + screenHeight;
			} else {
				// Move to bottom of previous column
				xOffset = -screenWidth;
				yOffset = docHeight - screenHeight + top;
			}
		} else {
			// Retreat by 90% of the screen height downwards (in case lines are partially cut off)
			xOffset = 0;
			yOffset = -smartAdvanceAmount(screenHeight, top);
		}
		mScrollerLastX = mScrollerLastY = 0;
		mScroller.startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
		post(this);
	}

	public void resetupChildren() {
		for (int i = 0; i < mChildViews.size(); i++)
			onChildSetup(mChildViews.keyAt(i), mChildViews.valueAt(i));
	}

	public void applyToChildren(ViewMapper mapper) {
		for (int i = 0; i < mChildViews.size(); i++)
			mapper.applyToView(mChildViews.valueAt(i));
	}

	public void refresh(boolean reflow) {
		mReflow = reflow;

		mScale = 1.0f;
		mXScroll = mYScroll = 0;

		int numChildren = mChildViews.size();
		for (int i = 0; i < numChildren; i++) {
			View v = mChildViews.valueAt(i);
			onNotInUse(v);
			removeViewInLayout(v);
		}
		mChildViews.clear();
		mViewCache.clear();

		requestLayout();
	}

	protected void onChildSetup(int i, View v) {
	}

	protected void onMoveToChild(int i) {
	}

	protected void onSettle(View v) {
	};

	protected void onUnsettle(View v) {
	};

	protected void onNotInUse(View v) {
	};

	protected void onScaleChild(View v, Float scale) {
	};

	public View getDisplayedView() {
		return mChildViews.get(mCurrent);
	}

	public void run() {
		if (!mScroller.isFinished()) {
			mScroller.computeScrollOffset();
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();
			mXScroll += x - mScrollerLastX;
			mYScroll += y - mScrollerLastY;
			mScrollerLastX = x;
			mScrollerLastY = y;
			requestLayout();
			post(this);
		} else if (!mUserInteracting) {
			// End of an inertial scroll and the user is not interacting.
			// The layout is stable
			View v = mChildViews.get(mCurrent);
			if (v != null)
				postSettle(v);
		}
	}

	public boolean onDown(MotionEvent arg0) {
		mScroller.forceFinished(true);
		return true;
	}

	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		if (mScrollDisabled)
			return true;

		View v = mChildViews.get(mCurrent);
		if (v != null) {
			Rect bounds = getScrollBounds(v);
			switch (directionOfTravel(velocityX, velocityY)) {
			case MOVING_LEFT:
				if (bounds.left >= 0) {
					// Fling off to the left bring next view onto screen
					View vl = mChildViews.get(mCurrent + 1);

					if (vl != null) {
						slideViewOntoScreen(vl);
						return true;
					}
				}
				break;
			case MOVING_RIGHT:
				if (bounds.right <= 0) {
					// Fling off to the right bring previous view onto screen
					View vr = mChildViews.get(mCurrent - 1);

					if (vr != null) {
						slideViewOntoScreen(vr);
						return true;
					}
				}
				break;
			}
			mScrollerLastX = mScrollerLastY = 0;
			// If the page has been dragged out of bounds then we want to spring back
			// nicely. fling jumps back into bounds instantly, so we don't want to use
			// fling in that case. On the other hand, we don't want to forgo a fling
			// just because of a slightly off-angle drag taking us out of bounds other
			// than in the direction of the drag, so we test for out of bounds only
			// in the direction of travel.
			//
			// Also don't fling if out of bounds in any direction by more than fling
			// margin
			Rect expandedBounds = new Rect(bounds);
			expandedBounds.inset(-FLING_MARGIN, -FLING_MARGIN);

			if (withinBoundsInDirectionOfTravel(bounds, velocityX, velocityY) && expandedBounds.contains(0, 0)) {
				mScroller.fling(0, 0, (int) velocityX, (int) velocityY, bounds.left, bounds.right, bounds.top,
						bounds.bottom);
				post(this);
			}
		}

		return true;
	}

	public void onLongPress(MotionEvent e) {
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if (!mScrollDisabled) {
			mXScroll -= distanceX;
			mYScroll -= distanceY;
			requestLayout();
		}
		return true;
	}

	public void onShowPress(MotionEvent e) {
	}

	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	public boolean onScale(ScaleGestureDetector detector) {
		float previousScale = mScale;
		float scale_factor = mReflow ? REFLOW_SCALE_FACTOR : 1.0f;
		float min_scale = MIN_SCALE * scale_factor;
		float max_scale = MAX_SCALE * scale_factor;
		mScale = Math.min(Math.max(mScale * detector.getScaleFactor(), min_scale), max_scale);

		if (mReflow) {
			applyToChildren(new ViewMapper() {
				@Override
				void applyToView(View view) {
					onScaleChild(view, mScale);
				}
			});
		} else {
			float factor = mScale / previousScale;

			View v = mChildViews.get(mCurrent);
			if (v != null) {
				// Work out the focus point relative to the view top left
				int viewFocusX = (int) detector.getFocusX() - (v.getLeft() + mXScroll);
				int viewFocusY = (int) detector.getFocusY() - (v.getTop() + mYScroll);
				// Scroll to maintain the focus point
				mXScroll += viewFocusX - viewFocusX * factor;
				mYScroll += viewFocusY - viewFocusY * factor;
				requestLayout();
			}
		}
		return true;
	}

	public boolean onScaleBegin(ScaleGestureDetector detector) {
		mScaling = true;
		// Ignore any scroll amounts yet to be accounted for: the
		// screen is not showing the effect of them, so they can
		// only confuse the user
		mXScroll = mYScroll = 0;
		// Avoid jump at end of scaling by disabling scrolling
		// until the next start of gesture
		mScrollDisabled = true;
		return true;
	}

	public void onScaleEnd(ScaleGestureDetector detector) {
		mScaling = false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		mScaleGestureDetector.onTouchEvent(event);

		if (!mScaling)
			mGestureDetector.onTouchEvent(event);

		if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			mUserInteracting = true;
		}
		if (event.getActionMasked() == MotionEvent.ACTION_UP) {
			mScrollDisabled = false;
			mUserInteracting = false;

			View v = mChildViews.get(mCurrent);
			if (v != null) {
				if (mScroller.isFinished()) {
					// If, at the end of user interaction, there is no
					// current inertial scroll in operation then animate
					// the view onto screen if necessary
					slideViewOntoScreen(v);
				}

				if (mScroller.isFinished()) {
					// If still there is no inertial scroll in operation
					// then the layout is stable
					postSettle(v);
				}
			}
		}

		requestLayout();
		return true;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int n = getChildCount();
		for (int i = 0; i < n; i++)
			measureView(getChildAt(i));
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		View cv = mChildViews.get(mCurrent);
		Point cvOffset;

		if (!mResetLayout) {
			// Move to next or previous if current is sufficiently off center
			if (cv != null) {
				cvOffset = subScreenSizeOffset(cv);
				// cv.getRight() may be out of date with the current scale
				// so add left to the measured width for the correct position
				if (cv.getLeft() + cv.getMeasuredWidth() + cvOffset.x + GAP / 2 + mXScroll < getWidth() / 2
						&& mCurrent + 1 < mAdapter.getCount()) {
					postUnsettle(cv);
					// post to invoke test for end of animation
					// where we must set hq area for the new current view
					post(this);

					mCurrent++;
					onMoveToChild(mCurrent);
				}

				if (cv.getLeft() - cvOffset.x - GAP / 2 + mXScroll >= getWidth() / 2 && mCurrent > 0) {
					postUnsettle(cv);
					// post to invoke test for end of animation
					// where we must set hq area for the new current view
					post(this);

					mCurrent--;
					onMoveToChild(mCurrent);
				}
			}

			// Remove not needed children and hold them for reuse
			int numChildren = mChildViews.size();
			int childIndices[] = new int[numChildren];
			for (int i = 0; i < numChildren; i++)
				childIndices[i] = mChildViews.keyAt(i);

			for (int i = 0; i < numChildren; i++) {
				int ai = childIndices[i];
				if (ai < mCurrent - 1 || ai > mCurrent + 1) {
					View v = mChildViews.get(ai);
					onNotInUse(v);
					mViewCache.add(v);
					removeViewInLayout(v);
					mChildViews.remove(ai);
				}
			}
		} else {
			mResetLayout = false;
			mXScroll = mYScroll = 0;

			// Remove all children and hold them for reuse
			int numChildren = mChildViews.size();
			for (int i = 0; i < numChildren; i++) {
				View v = mChildViews.valueAt(i);
				onNotInUse(v);
				mViewCache.add(v);
				removeViewInLayout(v);
			}
			mChildViews.clear();
			// post to ensure generation of hq area
			post(this);
		}

		// Ensure current view is present
		int cvLeft, cvRight, cvTop, cvBottom;
		boolean notPresent = (mChildViews.get(mCurrent) == null);
		cv = getOrCreateChild(mCurrent);
		// When the view is sub-screen-size in either dimension we
		// offset it to center within the screen area, and to keep
		// the views spaced out
		cvOffset = subScreenSizeOffset(cv);
		if (notPresent) {
			// Main item not already present. Just place it top left
			cvLeft = cvOffset.x;
			cvTop = cvOffset.y;
		} else {
			// Main item already present. Adjust by scroll offsets
			cvLeft = cv.getLeft() + mXScroll;
			cvTop = cv.getTop() + mYScroll;
		}
		// Scroll values have been accounted for
		mXScroll = mYScroll = 0;
		cvRight = cvLeft + cv.getMeasuredWidth();
		cvBottom = cvTop + cv.getMeasuredHeight();

		if (!mUserInteracting && mScroller.isFinished()) {
			Point corr = getCorrection(getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
			cvRight += corr.x;
			cvLeft += corr.x;
			cvTop += corr.y;
			cvBottom += corr.y;
		} else if (cv.getMeasuredHeight() <= getHeight()) {
			// When the current view is as small as the screen in height, clamp
			// it vertically
			Point corr = getCorrection(getScrollBounds(cvLeft, cvTop, cvRight, cvBottom));
			cvTop += corr.y;
			cvBottom += corr.y;
		}

		cv.layout(cvLeft, cvTop, cvRight, cvBottom);

		if (mCurrent > 0) {
			View lv = getOrCreateChild(mCurrent - 1);
			Point leftOffset = subScreenSizeOffset(lv);
			int gap = leftOffset.x + GAP + cvOffset.x;
			lv.layout(cvLeft - lv.getMeasuredWidth() - gap, (cvBottom + cvTop - lv.getMeasuredHeight()) / 2, cvLeft
					- gap, (cvBottom + cvTop + lv.getMeasuredHeight()) / 2);
		}

		if (mCurrent + 1 < mAdapter.getCount()) {
			View rv = getOrCreateChild(mCurrent + 1);
			Point rightOffset = subScreenSizeOffset(rv);
			int gap = cvOffset.x + GAP + rightOffset.x;
			rv.layout(cvRight + gap, (cvBottom + cvTop - rv.getMeasuredHeight()) / 2, cvRight + rv.getMeasuredWidth()
					+ gap, (cvBottom + cvTop + rv.getMeasuredHeight()) / 2);
		}

		invalidate();
	}

	@Override
	public Adapter getAdapter() {
		return mAdapter;
	}

	@Override
	public View getSelectedView() {
		throw new UnsupportedOperationException("Not supported");
	}

	@Override
	public void setAdapter(Adapter adapter) {
		mAdapter = adapter;
		mChildViews.clear();
		removeAllViewsInLayout();
		requestLayout();
	}

	@Override
	public void setSelection(int arg0) {
		throw new UnsupportedOperationException("Not supported");
	}

	private View getCached() {
		if (mViewCache.size() == 0)
			return null;
		else
			return mViewCache.removeFirst();
	}

	private View getOrCreateChild(int i) {
		View v = mChildViews.get(i);
		if (v == null) {
			v = mAdapter.getView(i, getCached(), this);
			addAndMeasureChild(i, v);
		}
		onChildSetup(i, v);
		onScaleChild(v, mScale);

		return v;
	}

	private void addAndMeasureChild(int i, View v) {
		LayoutParams params = v.getLayoutParams();
		if (params == null) {
			params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		}
		addViewInLayout(v, 0, params, true);
		mChildViews.append(i, v); // Record the view against it's adapter index
		measureView(v);
	}

	private void measureView(View v) {
		// See what size the view wants to be
		v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

		if (!mReflow) {
			// Work out a scale that will fit it to this view
			float scale = Math.min((float) getWidth() / (float) v.getMeasuredWidth(),
					(float) getHeight() / (float) v.getMeasuredHeight());
			// Use the fitting values scaled by our current scale factor
			v.measure(View.MeasureSpec.EXACTLY | (int) (v.getMeasuredWidth() * scale * mScale),
					View.MeasureSpec.EXACTLY | (int) (v.getMeasuredHeight() * scale * mScale));
		} else {
			v.measure(View.MeasureSpec.EXACTLY | (int) (v.getMeasuredWidth()),
					View.MeasureSpec.EXACTLY | (int) (v.getMeasuredHeight()));
		}
	}

	private Rect getScrollBounds(int left, int top, int right, int bottom) {
		int xmin = getWidth() - right;
		int xmax = -left;
		int ymin = getHeight() - bottom;
		int ymax = -top;

		// In either dimension, if view smaller than screen then
		// constrain it to be central
		if (xmin > xmax)
			xmin = xmax = (xmin + xmax) / 2;
		if (ymin > ymax)
			ymin = ymax = (ymin + ymax) / 2;

		return new Rect(xmin, ymin, xmax, ymax);
	}

	private Rect getScrollBounds(View v) {
		// There can be scroll amounts not yet accounted for in
		// onLayout, so add mXScroll and mYScroll to the current
		// positions when calculating the bounds.
		return getScrollBounds(v.getLeft() + mXScroll, v.getTop() + mYScroll, v.getLeft() + v.getMeasuredWidth()
				+ mXScroll, v.getTop() + v.getMeasuredHeight() + mYScroll);
	}

	private Point getCorrection(Rect bounds) {
		return new Point(Math.min(Math.max(0, bounds.left), bounds.right), Math.min(Math.max(0, bounds.top),
				bounds.bottom));
	}

	private void postSettle(final View v) {
		// onSettle and onUnsettle are posted so that the calls
		// wont be executed until after the system has performed
		// layout.
		post(new Runnable() {
			public void run() {
				onSettle(v);
			}
		});
	}

	private void postUnsettle(final View v) {
		post(new Runnable() {
			public void run() {
				onUnsettle(v);
			}
		});
	}

	private void slideViewOntoScreen(View v) {
		Point corr = getCorrection(getScrollBounds(v));
		if (corr.x != 0 || corr.y != 0) {
			mScrollerLastX = mScrollerLastY = 0;
			mScroller.startScroll(0, 0, corr.x, corr.y, 400);
			post(this);
		}
	}

	private Point subScreenSizeOffset(View v) {
		return new Point(Math.max((getWidth() - v.getMeasuredWidth()) / 2, 0), Math.max(
				(getHeight() - v.getMeasuredHeight()) / 2, 0));
	}

	private static int directionOfTravel(float vx, float vy) {
		if (Math.abs(vx) > 2 * Math.abs(vy))
			return (vx > 0) ? MOVING_RIGHT : MOVING_LEFT;
		else if (Math.abs(vy) > 2 * Math.abs(vx))
			return (vy > 0) ? MOVING_DOWN : MOVING_UP;
		else
			return MOVING_DIAGONALLY;
	}

	private static boolean withinBoundsInDirectionOfTravel(Rect bounds, float vx, float vy) {
		switch (directionOfTravel(vx, vy)) {
		case MOVING_DIAGONALLY:
			return bounds.contains(0, 0);
		case MOVING_LEFT:
			return bounds.left <= 0;
		case MOVING_RIGHT:
			return bounds.right >= 0;
		case MOVING_UP:
			return bounds.top <= 0;
		case MOVING_DOWN:
			return bounds.bottom >= 0;
		default:
			throw new NoSuchElementException();
		}
	}
}

class MuPDFReaderView extends ReaderView {
	private final Context mContext;
	private boolean mSelecting = false;
	private boolean tapDisabled = false;
	private int tapPageMargin;

	protected void onTapMainDocArea() {
	}

	protected void onDocMotion() {
	}

	public void setSelectionMode(boolean b) {
		mSelecting = b;
	}

	public MuPDFReaderView(Activity act) {
		super(act);
		mContext = act;
		// Get the screen size etc to customise tap margins.
		// We calculate the size of 1 inch of the screen for tapping.
		// On some devices the dpi values returned are wrong, so we
		// sanity check it: we first restrict it so that we are never
		// less than 100 pixels (the smallest Android device screen
		// dimension I've seen is 480 pixels or so). Then we check
		// to ensure we are never more than 1/5 of the screen width.
		DisplayMetrics dm = new DisplayMetrics();
		act.getWindowManager().getDefaultDisplay().getMetrics(dm);
		tapPageMargin = (int) dm.xdpi;
		if (tapPageMargin < 100)
			tapPageMargin = 100;
		if (tapPageMargin > dm.widthPixels / 5)
			tapPageMargin = dm.widthPixels / 5;
	}

	public boolean onSingleTapUp(MotionEvent e) {
		if (!mSelecting && !tapDisabled) {
			if (e.getX() < tapPageMargin) {
				super.smartMoveBackwards();
			} else if (e.getX() > super.getWidth() - tapPageMargin) {
				super.smartMoveForwards();
			} else if (e.getY() < tapPageMargin) {
				super.smartMoveBackwards();
			} else if (e.getY() > super.getHeight() - tapPageMargin) {
				super.smartMoveForwards();
			} else {
				onTapMainDocArea();
			}
		}
		return super.onSingleTapUp(e);
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if (!tapDisabled)
			onDocMotion();

		return super.onScroll(e1, e2, distanceX, distanceY);
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		if (!mSelecting)
			return super.onFling(e1, e2, velocityX, velocityY);
		else
			return true;
	}

	public boolean onScaleBegin(ScaleGestureDetector d) {
		// Disabled showing the buttons until next touch.
		// Not sure why this is needed, but without it
		// pinch zoom can make the buttons appear
		tapDisabled = true;
		return super.onScaleBegin(d);
	}

	public boolean onTouchEvent(MotionEvent event) {
		if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
			tapDisabled = false;

		return super.onTouchEvent(event);
	}

	protected void onSettle(View v) {
		// When the layout has settled ask the page to render
		// in HQ
		((MuPDFPageView) v).addHq(false);
	}

	protected void onUnsettle(View v) {
		// When something changes making the previous settled view
		// no longer appropriate, tell the page to remove HQ
		((MuPDFPageView) v).removeHq();
	}

	@Override
	protected void onNotInUse(View v) {
		((MuPDFPageView) v).releaseResources();
	}

	@Override
	protected void onScaleChild(View v, Float scale) {
		((MuPDFPageView) v).setScale(scale);
	}
}
