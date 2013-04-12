package lah.texpert;

import lah.texpert.TextView.OnEditorActionListener;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.KeyListener;
import android.text.style.SuggestionSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupWindow;

public class Editor {

	static final int BLINK = 500;

	private static final float[] TEMP_POSITION = new float[2];

	// Cursor Controllers.
	InsertionPointCursorController mInsertionPointCursorController;
	ActionMode mSelectionActionMode;
	private boolean mInsertionControllerEnabled;

	InputContentType mInputContentType = new InputContentType();
	InputMethodState mInputMethodState = new InputMethodState();

	int mLastLayoutHeight;

	boolean mFrozenWithFocus;
	boolean mSelectionMoved;
	boolean mTouchFocusSelected;

	KeyListener mKeyListener;
	int mInputType = EditorInfo.TYPE_NULL;

	boolean mDiscardNextActionUp;
	boolean mIgnoreActionUpEvent;

	long mShowCursor;
	Blink mBlink;

	boolean mCursorVisible = true;
	boolean mSelectAllOnFocus;
	boolean mTextIsSelectable;

	boolean mInBatchEditControllers;
	boolean mShowSoftInputOnFocus = true;
	boolean mPreserveDetachedSelection;
	boolean mTemporaryDetach;

	final Drawable[] mCursorDrawable = new Drawable[2];
	int mCursorCount; // Current number of used mCursorDrawable: 0 (resource=0), 1 or 2 (split)

	private Drawable mSelectHandleCenter;

	// Global listener that detects changes in the global position of the TextView
	private PositionListener mPositionListener = new PositionListener();

	float mLastDownPositionX, mLastDownPositionY;
	Callback mCustonullCallback;

	private Rect mTempRect;

	private TextView mTextView;

	Editor(TextView textView) {
		mTextView = textView;
	}

	void onAttachedToWindow() {
		mTemporaryDetach = false;

		final ViewTreeObserver observer = mTextView.getViewTreeObserver();
		// No need to create the controller.
		// The get method will add the listener on controller creation.
		if (mInsertionPointCursorController != null) {
			observer.addOnTouchModeChangeListener(mInsertionPointCursorController);
		}
		if (mTextView.hasTransientState() && mTextView.getSelectionStart() != mTextView.getSelectionEnd()) {
			// Since transient state is reference counted make sure it stays matched
			// with our own calls to it for managing selection.
			// The action mode callback will set this back again when/if the action mode starts.
			mTextView.setHasTransientState(false);
		}
	}

	void onDetachedFromWindow() {
		if (mBlink != null) {
			mBlink.removeCallbacks(mBlink);
		}

		if (mInsertionPointCursorController != null) {
			mInsertionPointCursorController.onDetached();
		}
		mPreserveDetachedSelection = true;
		hideControllers();
		mPreserveDetachedSelection = false;
		mTemporaryDetach = false;
	}

	boolean isCursorVisible() {
		// The default value is true, even when there is no associated Editor
		return mCursorVisible;
	}

	void prepareCursorControllers() {
		boolean windowSupportsHandles = false;

		ViewGroup.LayoutParams params = mTextView.getRootView().getLayoutParams();
		if (params instanceof WindowManager.LayoutParams) {
			WindowManager.LayoutParams windowParams = (WindowManager.LayoutParams) params;
			windowSupportsHandles = windowParams.type < WindowManager.LayoutParams.FIRST_SUB_WINDOW
					|| windowParams.type > WindowManager.LayoutParams.LAST_SUB_WINDOW;
		}

		boolean enabled = windowSupportsHandles && mTextView.getLayout() != null;
		mInsertionControllerEnabled = enabled && isCursorVisible();

		if (!mInsertionControllerEnabled) {
			hideInsertionPointCursorController();
			if (mInsertionPointCursorController != null) {
				mInsertionPointCursorController.onDetached();
				mInsertionPointCursorController = null;
			}
		}
	}

	private void hideInsertionPointCursorController() {
		if (mInsertionPointCursorController != null) {
			mInsertionPointCursorController.hide();
		}
	}

	/**
	 * Hides the insertion controller and stops text selection mode, hiding the selection controller
	 */
	void hideControllers() {
		hideCursorControllers();
	}

	private void hideCursorControllers() {
		hideInsertionPointCursorController();
	}

	void onScreenStateChanged(int screenState) {
		switch (screenState) {
		case View.SCREEN_STATE_ON:
			resumeBlink();
			break;
		case View.SCREEN_STATE_OFF:
			suspendBlink();
			break;
		}
	}

	private void suspendBlink() {
		if (mBlink != null) {
			mBlink.cancel();
		}
	}

	private void resumeBlink() {
		if (mBlink != null) {
			mBlink.uncancel();
			makeBlink();
		}
	}

	private PositionListener getPositionListener() {
		return mPositionListener;
	}

	private interface TextViewPositionListener {
		public void updatePosition(int parentPositionX, int parentPositionY, boolean parentPositionChanged,
				boolean parentScrolled);
	}

	private boolean isPositionVisible(int positionX, int positionY) {
		synchronized (TEMP_POSITION) {
			final float[] position = TEMP_POSITION;
			position[0] = positionX;
			position[1] = positionY;
			View view = mTextView;

			while (view != null) {
				if (view != mTextView) {
					// Local scroll is already taken into account in positionX/Y
					position[0] -= view.getScrollX();
					position[1] -= view.getScrollY();
				}

				if (position[0] < 0 || position[1] < 0 || position[0] > view.getWidth()
						|| position[1] > view.getHeight()) {
					return false;
				}

				if (!view.getMatrix().isIdentity()) {
					view.getMatrix().mapPoints(position);
				}

				position[0] += view.getLeft();
				position[1] += view.getTop();

				final ViewParent parent = view.getParent();
				if (parent instanceof View) {
					view = (View) parent;
				} else {
					// We've reached the ViewRoot, stop iterating
					view = null;
				}
			}
		}

		// We've been able to walk up the view hierarchy and the position was never clipped
		return true;
	}

	/**
	 * Returns true if the screen coordinates position (x,y) corresponds to a character displayed in the view. Returns
	 * false when the position is in the empty space of left/right of text.
	 */
	private boolean isPositionOnText(float x, float y) {
		Layout layout = mTextView.getLayout();
		if (layout == null)
			return false;

		final int line = mTextView.getLineAtCoordinate(y);
		x = mTextView.convertToLocalHorizontalCoordinate(x);

		if (x < layout.getLineLeft(line))
			return false;
		if (x > layout.getLineRight(line))
			return false;
		return true;
	}

	public boolean performLongClick(boolean handled) {
		// Long press in empty space moves cursor and shows the Paste affordance if available.
		if (!handled && !isPositionOnText(mLastDownPositionX, mLastDownPositionY) && mInsertionControllerEnabled) {
			final int offset = mTextView.getOffsetForPosition(mLastDownPositionX, mLastDownPositionY);
			Selection.setSelection((Spannable) mTextView.getText(), offset);
			getInsertionController().showWithActionPopup();
			handled = true;
		}
		return handled;
	}

	void onFocusChanged(boolean focused, int direction) {
		mShowCursor = SystemClock.uptimeMillis();
		ensureEndedBatchEdit();

		if (focused) {
			int selStart = mTextView.getSelectionStart();
			int selEnd = mTextView.getSelectionEnd();
			if (!mFrozenWithFocus || (selStart < 0 || selEnd < 0)) {
				// If a tap was used to give focus to that view, move cursor at tap position.
				// Has to be done before onTakeFocus, which can be overloaded.
				final int lastTapPosition = getLastTapPosition();
				if (lastTapPosition >= 0) {
					Selection.setSelection((Spannable) mTextView.getText(), lastTapPosition);
				}

				// Note this may have to be moved out of the Editor class
				// MovementMethod mMovement = mTextView.getMovementMethod();
				// if (mMovement != null) {
				// mMovement.onTakeFocus(mTextView, (Spannable) mTextView.getText(), direction);
				// }

				// The DecorView does not have focus when the 'Done' ExtractEditText button is
				// pressed. Since it is the ViewAncestor's mView, it requests focus before
				// ExtractEditText clears focus, which gives focus to the ExtractEditText.
				// This special case ensure that we keep current selection in that case.
				// It would be better to know why the DecorView does not have focus at that time.
				// if (((mTextView instanceof ExtractEditText) || mSelectionMoved) && selStart >= 0 && selEnd >= 0) {
				// /*
				// * Someone intentionally set the selection, so let them do whatever it is that they wanted to do
				// * instead of the default on-focus behavior. We reset the selection here instead of just skipping
				// * the onTakeFocus() call because some movement methods do something other than just setting the
				// * selection in theirs and we still need to go through that path.
				// */
				// Selection.setSelection((Spannable) mTextView.getText(), selStart, selEnd);
				// }

				if (mSelectAllOnFocus) {
					mTextView.selectAllText();
				}

				mTouchFocusSelected = true;
			}

			mFrozenWithFocus = false;
			mSelectionMoved = false;

			makeBlink();
		} else {
			// Don't leave us in the middle of a batch edit.
			mTextView.onEndBatchEdit();
			if (mTemporaryDetach)
				mPreserveDetachedSelection = true;
			hideControllers();
			if (mTemporaryDetach)
				mPreserveDetachedSelection = false;
			downgradeEasyCorrectionSpans();
		}
	}

	/**
	 * Downgrades to simple suggestions all the easy correction spans that are not a spell check span.
	 */
	private void downgradeEasyCorrectionSpans() {
		CharSequence text = mTextView.getText();
		if (text instanceof Spannable) {
			Spannable spannable = (Spannable) text;
			SuggestionSpan[] suggestionSpans = spannable.getSpans(0, spannable.length(), SuggestionSpan.class);
			for (int i = 0; i < suggestionSpans.length; i++) {
				int flags = suggestionSpans[i].getFlags();
				if ((flags & SuggestionSpan.FLAG_EASY_CORRECT) != 0 && (flags & SuggestionSpan.FLAG_MISSPELLED) == 0) {
					flags &= ~SuggestionSpan.FLAG_EASY_CORRECT;
					suggestionSpans[i].setFlags(flags);
				}
			}
		}
	}

	void sendOnTextChanged(int start, int after) {
		// Hide the controllers as soon as text is modified (typing, procedural...)
		// We do not hide the span controllers, since they can be added when a new text is
		// inserted into the text view (voice IME).
		hideCursorControllers();
	}

	private int getLastTapPosition() {
		// No need to create the controller at that point, no last tap position saved
		return -1;
	}

	void onWindowFocusChanged(boolean hasWindowFocus) {
		if (hasWindowFocus) {
			if (mBlink != null) {
				mBlink.uncancel();
				makeBlink();
			}
		} else {
			if (mBlink != null) {
				mBlink.cancel();
			}
			if (mInputContentType != null) {
				mInputContentType.enterDown = false;
			}
			// Order matters! Must be done before onParentLostFocus to rely on isShowingUp
			hideControllers();
			// Don't leave us in the middle of a batch edit. Same as in onFocusChanged
			ensureEndedBatchEdit();
		}
	}

	void onTouchEvent(MotionEvent event) {
		if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			mLastDownPositionX = event.getX();
			mLastDownPositionY = event.getY();

			// Reset this state; it will be re-set if super.onTouchEvent
			// causes focus to move to the view.
			mTouchFocusSelected = false;
			mIgnoreActionUpEvent = false;
		}
	}

	public void beginBatchEdit() {
		mInBatchEditControllers = true;
		final InputMethodState ims = mInputMethodState;
		if (ims != null) {
			int nesting = ++ims.mBatchEditNesting;
			if (nesting == 1) {
				ims.mCursorChanged = false;
				ims.mChangedDelta = 0;
				if (ims.mContentChanged) {
					// We already have a pending change from somewhere else,
					// so turn this into a full update.
					ims.mChangedStart = 0;
					ims.mChangedEnd = mTextView.getText().length();
				} else {
					ims.mChangedStart = EXTRACT_UNKNOWN;
					ims.mChangedEnd = EXTRACT_UNKNOWN;
					ims.mContentChanged = false;
				}
				mTextView.onBeginBatchEdit();
			}
		}
	}

	public void endBatchEdit() {
		mInBatchEditControllers = false;
		final InputMethodState ims = mInputMethodState;
		if (ims != null) {
			int nesting = --ims.mBatchEditNesting;
			if (nesting == 0) {
				finishBatchEdit(ims);
			}
		}
	}

	void ensureEndedBatchEdit() {
		final InputMethodState ims = mInputMethodState;
		if (ims != null && ims.mBatchEditNesting != 0) {
			ims.mBatchEditNesting = 0;
			finishBatchEdit(ims);
		}
	}

	void finishBatchEdit(final InputMethodState ims) {
		mTextView.onEndBatchEdit();

		if (ims.mContentChanged || ims.mSelectionModeChanged) {
			mTextView.updateAfterEdit();
			reportExtractedText();
		} else if (ims.mCursorChanged) {
			// Cheezy way to get us to report the current cursor location.
			mTextView.invalidateCursor();
		}
	}

	static final int EXTRACT_NOTHING = -2;
	static final int EXTRACT_UNKNOWN = -1;

	boolean extractText(ExtractedTextRequest request, ExtractedText outText) {
		return extractTextInternal(request, EXTRACT_UNKNOWN, EXTRACT_UNKNOWN, EXTRACT_UNKNOWN, outText);
	}

	private boolean extractTextInternal(ExtractedTextRequest request, int partialStartOffset, int partialEndOffset,
			int delta, ExtractedText outText) {
		final CharSequence content = mTextView.getText();
		if (content != null) {
			if (partialStartOffset != EXTRACT_NOTHING) {
				final int N = content.length();
				if (partialStartOffset < 0) {
					outText.partialStartOffset = outText.partialEndOffset = -1;
					partialStartOffset = 0;
					partialEndOffset = N;
				} else {
					// Now use the delta to determine the actual amount of text
					// we need.
					partialEndOffset += delta;
					// Adjust offsets to ensure we contain full spans.
					if (content instanceof Spanned) {
						Spanned spanned = (Spanned) content;
						Object[] spans = spanned.getSpans(partialStartOffset, partialEndOffset, ParcelableSpan.class);
						int i = spans.length;
						while (i > 0) {
							i--;
							int j = spanned.getSpanStart(spans[i]);
							if (j < partialStartOffset)
								partialStartOffset = j;
							j = spanned.getSpanEnd(spans[i]);
							if (j > partialEndOffset)
								partialEndOffset = j;
						}
					}
					outText.partialStartOffset = partialStartOffset;
					outText.partialEndOffset = partialEndOffset - delta;

					if (partialStartOffset > N) {
						partialStartOffset = N;
					} else if (partialStartOffset < 0) {
						partialStartOffset = 0;
					}
					if (partialEndOffset > N) {
						partialEndOffset = N;
					} else if (partialEndOffset < 0) {
						partialEndOffset = 0;
					}
				}
				if ((request.flags & InputConnection.GET_TEXT_WITH_STYLES) != 0) {
					outText.text = content.subSequence(partialStartOffset, partialEndOffset);
				} else {
					outText.text = TextUtils.substring(content, partialStartOffset, partialEndOffset);
				}
			} else {
				outText.partialStartOffset = 0;
				outText.partialEndOffset = 0;
				outText.text = "";
			}
			outText.flags = 0;
			// if (MetaKeyKeyListener.getMetaState(content, MetaKeyKeyListener.META_SELECTING) != 0) {
			// outText.flags |= ExtractedText.FLAG_SELECTING;
			// }
			// if (mTextView.isSingleLine()) {
			// outText.flags |= ExtractedText.FLAG_SINGLE_LINE;
			// }
			outText.startOffset = 0;
			outText.selectionStart = mTextView.getSelectionStart();
			outText.selectionEnd = mTextView.getSelectionEnd();
			return true;
		}
		return false;
	}

	boolean reportExtractedText() {
		final Editor.InputMethodState ims = mInputMethodState;
		if (ims != null) {
			final boolean contentChanged = ims.mContentChanged;
			if (contentChanged || ims.mSelectionModeChanged) {
				ims.mContentChanged = false;
				ims.mSelectionModeChanged = false;
				final ExtractedTextRequest req = ims.mExtractedTextRequest;
				if (req != null) {
					InputMethodManager imm = (InputMethodManager) mTextView.getContext().getSystemService(
							Context.INPUT_METHOD_SERVICE);
					if (imm != null) {
						if (TextView.DEBUG_EXTRACT)
							Log.v(TextView.LOG_TAG, "Retrieving extracted start=" + ims.mChangedStart + " end="
									+ ims.mChangedEnd + " delta=" + ims.mChangedDelta);
						if (ims.mChangedStart < 0 && !contentChanged) {
							ims.mChangedStart = EXTRACT_NOTHING;
						}
						if (extractTextInternal(req, ims.mChangedStart, ims.mChangedEnd, ims.mChangedDelta,
								ims.mExtractedText)) {
							if (TextView.DEBUG_EXTRACT)
								Log.v(TextView.LOG_TAG, "Reporting extracted start="
										+ ims.mExtractedText.partialStartOffset + " end="
										+ ims.mExtractedText.partialEndOffset + ": " + ims.mExtractedText.text);

							imm.updateExtractedText(mTextView, req.token, ims.mExtractedText);
							ims.mChangedStart = EXTRACT_UNKNOWN;
							ims.mChangedEnd = EXTRACT_UNKNOWN;
							ims.mChangedDelta = 0;
							ims.mContentChanged = false;
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	void onDraw(Canvas canvas, Layout layout, Path highlight, Paint highlightPaint, int cursorOffsetVertical) {
		final int selectionStart = mTextView.getSelectionStart();
		final int selectionEnd = mTextView.getSelectionEnd();

		final InputMethodState ims = mInputMethodState;
		if (ims != null && ims.mBatchEditNesting == 0) {
			InputMethodManager imm = (InputMethodManager) mTextView.getContext().getSystemService(
					Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				if (imm.isActive(mTextView)) {
					boolean reported = false;
					if (ims.mContentChanged || ims.mSelectionModeChanged) {
						// We are in extract mode and the content has changed
						// in some way... just report complete new text to the
						// input method.
						reported = reportExtractedText();
					}
					if (!reported && highlight != null) {
						int candStart = -1;
						int candEnd = -1;
						if (mTextView.getText() instanceof Spannable) {
							Spannable sp = (Spannable) mTextView.getText();
							candStart = EditableInputConnection.getComposingSpanStart(sp);
							candEnd = EditableInputConnection.getComposingSpanEnd(sp);
						}
						imm.updateSelection(mTextView, selectionStart, selectionEnd, candStart, candEnd);
					}
				}

				if (imm.isWatchingCursor(mTextView) && highlight != null) {
					highlight.computeBounds(ims.mTmpRectF, true);
					ims.mTmpOffset[0] = ims.mTmpOffset[1] = 0;

					canvas.getMatrix().mapPoints(ims.mTmpOffset);
					ims.mTmpRectF.offset(ims.mTmpOffset[0], ims.mTmpOffset[1]);

					ims.mTmpRectF.offset(0, cursorOffsetVertical);

					ims.mCursorRectInWindow.set((int) (ims.mTmpRectF.left + 0.5), (int) (ims.mTmpRectF.top + 0.5),
							(int) (ims.mTmpRectF.right + 0.5), (int) (ims.mTmpRectF.bottom + 0.5));

					imm.updateCursor(mTextView, ims.mCursorRectInWindow.left, ims.mCursorRectInWindow.top,
							ims.mCursorRectInWindow.right, ims.mCursorRectInWindow.bottom);
				}
			}
		}

		if (highlight != null && selectionStart == selectionEnd && mCursorCount > 0) {
			drawCursor(canvas, cursorOffsetVertical);
			// Rely on the drawable entirely, do not draw the cursor line.
			// Has to be done after the IMM related code above which relies on the highlight.
			highlight = null;
		}

		// TODO L.A.H. Unfortunately, hardware acceleration is not publicly accessible
		layout.draw(canvas, highlight, highlightPaint, cursorOffsetVertical);
	}

	private void drawCursor(Canvas canvas, int cursorOffsetVertical) {
		final boolean translate = cursorOffsetVertical != 0;
		if (translate)
			canvas.translate(0, cursorOffsetVertical);
		for (int i = 0; i < mCursorCount; i++) {
			mCursorDrawable[i].draw(canvas);
		}
		if (translate)
			canvas.translate(0, -cursorOffsetVertical);
	}

	void updateCursorsPositions() {
		if (mTextView.mCursorDrawableRes == 0) {
			mCursorCount = 0;
			return;
		}

		Layout layout = mTextView.getLayout();

		final int offset = mTextView.getSelectionStart();
		final int line = layout.getLineForOffset(offset);
		final int top = layout.getLineTop(line);
		final int bottom = layout.getLineTop(line + 1);

		mCursorCount = 1;

		int middle = bottom;
		if (mCursorCount == 2) {
			// Similar to what is done in {@link Layout.#getCursorPath(int, Path, CharSequence)}
			middle = (top + bottom) >> 1;
		}

		updateCursorPosition(0, top, middle, layout.getPrimaryHorizontal(offset));

		if (mCursorCount == 2) {
			updateCursorPosition(1, middle, bottom, layout.getSecondaryHorizontal(offset));
		}
	}

	private boolean extractedTextModeWillBeStarted() {
		// if (!(mTextView instanceof ExtractEditText)) {
		// final InputMethodManager imm = (InputMethodManager) mTextView.getContext().getSystemService(
		// Context.INPUT_METHOD_SERVICE);
		// return imm != null && imm.isFullscreenMode();
		// }
		return false;
	}

	void onTouchUpEvent(MotionEvent event) {
		boolean selectAllGotFocus = mSelectAllOnFocus && mTextView.didTouchFocusSelect();
		hideControllers();
		CharSequence text = mTextView.getText();
		if (!selectAllGotFocus && text.length() > 0) {
			// Move cursor
			final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());
			Selection.setSelection((Spannable) text, offset);
			if (!extractedTextModeWillBeStarted()) {
				if (hasInsertionController()) {
					getInsertionController().show();
				}
			}
		}
	}

	/**
	 * @return True if this view supports insertion handles.
	 */
	boolean hasInsertionController() {
		return mInsertionControllerEnabled;
	}

	InsertionPointCursorController getInsertionController() {
		if (!mInsertionControllerEnabled) {
			return null;
		}

		if (mInsertionPointCursorController == null) {
			mInsertionPointCursorController = new InsertionPointCursorController();

			final ViewTreeObserver observer = mTextView.getViewTreeObserver();
			observer.addOnTouchModeChangeListener(mInsertionPointCursorController);
		}

		return mInsertionPointCursorController;
	}

	private void updateCursorPosition(int cursorIndex, int top, int bottom, float horizontal) {
		if (mCursorDrawable[cursorIndex] == null)
			mCursorDrawable[cursorIndex] = mTextView.getResources().getDrawable(mTextView.mCursorDrawableRes);

		if (mTempRect == null)
			mTempRect = new Rect();
		mCursorDrawable[cursorIndex].getPadding(mTempRect);
		final int width = mCursorDrawable[cursorIndex].getIntrinsicWidth();
		horizontal = Math.max(0.5f, horizontal - 0.5f);
		final int left = (int) (horizontal) - mTempRect.left;
		mCursorDrawable[cursorIndex].setBounds(left, top - mTempRect.top, left + width, bottom + mTempRect.bottom);
	}

	void onScrollChanged() {
		if (mPositionListener != null) {
			mPositionListener.onScrollChanged();
		}
	}

	/**
	 * @return True when the TextView isFocused and has a valid zero-length selection (cursor).
	 */
	private boolean shouldBlink() {
		if (!isCursorVisible() || !mTextView.isFocused())
			return false;

		final int start = mTextView.getSelectionStart();
		if (start < 0)
			return false;

		final int end = mTextView.getSelectionEnd();
		if (end < 0)
			return false;

		return start == end;
	}

	void makeBlink() {
		if (shouldBlink()) {
			mShowCursor = SystemClock.uptimeMillis();
			if (mBlink == null)
				mBlink = new Blink();
			mBlink.removeCallbacks(mBlink);
			mBlink.postAtTime(mBlink, mShowCursor + BLINK);
		} else {
			if (mBlink != null)
				mBlink.removeCallbacks(mBlink);
		}
	}

	@SuppressLint("HandlerLeak")
	private class Blink extends Handler implements Runnable {
		private boolean mCancelled;

		public void run() {
			if (mCancelled) {
				return;
			}

			removeCallbacks(Blink.this);

			if (shouldBlink()) {
				if (mTextView.getLayout() != null) {
					mTextView.invalidateCursorPath();
				}

				postAtTime(this, SystemClock.uptimeMillis() + BLINK);
			}
		}

		void cancel() {
			if (!mCancelled) {
				removeCallbacks(Blink.this);
				mCancelled = true;
			}
		}

		void uncancel() {
			mCancelled = false;
		}
	}

	private static class DragLocalState {
		public TextView sourceTextView;
		public int start, end;

		@SuppressWarnings("unused")
		public DragLocalState(TextView sourceTextView, int start, int end) {
			this.sourceTextView = sourceTextView;
			this.start = start;
			this.end = end;
		}
	}

	void onDrop(DragEvent event) {
		StringBuilder content = new StringBuilder("");
		ClipData clipData = event.getClipData();
		final int itemCount = clipData.getItemCount();
		for (int i = 0; i < itemCount; i++) {
			Item item = clipData.getItemAt(i);
			content.append(item.coerceToStyledText(mTextView.getContext()));
		}

		final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());

		Object localState = event.getLocalState();
		DragLocalState dragLocalState = null;
		if (localState instanceof DragLocalState) {
			dragLocalState = (DragLocalState) localState;
		}
		boolean dragDropIntoItself = dragLocalState != null && dragLocalState.sourceTextView == mTextView;

		if (dragDropIntoItself) {
			if (offset >= dragLocalState.start && offset < dragLocalState.end) {
				// A drop inside the original selection discards the drop.
				return;
			}
		}

		final int originalLength = mTextView.getText().length();
		long minMax = mTextView.prepareSpacesAroundPaste(offset, offset, content);
		int min = TextUtils.unpackRangeStartFromLong(minMax);
		int max = TextUtils.unpackRangeEndFromLong(minMax);

		Selection.setSelection((Spannable) mTextView.getText(), max);
		mTextView.replaceText_internal(min, max, content);

		if (dragDropIntoItself) {
			int dragSourceStart = dragLocalState.start;
			int dragSourceEnd = dragLocalState.end;
			if (max <= dragSourceStart) {
				// Inserting text before selection has shifted positions
				final int shift = mTextView.getText().length() - originalLength;
				dragSourceStart += shift;
				dragSourceEnd += shift;
			}

			// Delete original selection
			mTextView.deleteText_internal(dragSourceStart, dragSourceEnd);

			// Make sure we do not leave two adjacent spaces.
			final int prevCharIdx = Math.max(0, dragSourceStart - 1);
			final int nextCharIdx = Math.min(mTextView.getText().length(), dragSourceStart + 1);
			if (nextCharIdx > prevCharIdx + 1) {
				CharSequence t = mTextView.getTransformedText(prevCharIdx, nextCharIdx);
				if (Character.isSpaceChar(t.charAt(0)) && Character.isSpaceChar(t.charAt(1))) {
					mTextView.deleteText_internal(prevCharIdx, prevCharIdx + 1);
				}
			}
		}
	}

	public void addSpanWatchers(Spannable text) {
		final int textLength = text.length();

		if (mKeyListener != null) {
			text.setSpan(mKeyListener, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
		}
	}

	private class PositionListener implements ViewTreeObserver.OnPreDrawListener {
		// 3 handles
		// 3 ActionPopup [replace, suggestion, easyedit] (suggestionsPopup first hides the others)
		private final int MAXIMUM_NUMBER_OF_LISTENERS = 6;
		private TextViewPositionListener[] mPositionListeners = new TextViewPositionListener[MAXIMUM_NUMBER_OF_LISTENERS];
		private boolean mCanMove[] = new boolean[MAXIMUM_NUMBER_OF_LISTENERS];
		private boolean mPositionHasChanged = true;
		// Absolute position of the TextView with respect to its parent window
		private int mPositionX, mPositionY;
		private int mNumberOfListeners;
		private boolean mScrollHasChanged;
		final int[] mTempCoords = new int[2];

		public void addSubscriber(TextViewPositionListener positionListener, boolean canMove) {
			if (mNumberOfListeners == 0) {
				updatePosition();
				ViewTreeObserver vto = mTextView.getViewTreeObserver();
				vto.addOnPreDrawListener(this);
			}

			int emptySlotIndex = -1;
			for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
				TextViewPositionListener listener = mPositionListeners[i];
				if (listener == positionListener) {
					return;
				} else if (emptySlotIndex < 0 && listener == null) {
					emptySlotIndex = i;
				}
			}

			mPositionListeners[emptySlotIndex] = positionListener;
			mCanMove[emptySlotIndex] = canMove;
			mNumberOfListeners++;
		}

		public void removeSubscriber(TextViewPositionListener positionListener) {
			for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
				if (mPositionListeners[i] == positionListener) {
					mPositionListeners[i] = null;
					mNumberOfListeners--;
					break;
				}
			}

			if (mNumberOfListeners == 0) {
				ViewTreeObserver vto = mTextView.getViewTreeObserver();
				vto.removeOnPreDrawListener(this);
			}
		}

		public int getPositionX() {
			return mPositionX;
		}

		public int getPositionY() {
			return mPositionY;
		}

		@Override
		public boolean onPreDraw() {
			updatePosition();

			for (int i = 0; i < MAXIMUM_NUMBER_OF_LISTENERS; i++) {
				if (mPositionHasChanged || mScrollHasChanged || mCanMove[i]) {
					TextViewPositionListener positionListener = mPositionListeners[i];
					if (positionListener != null) {
						positionListener.updatePosition(mPositionX, mPositionY, mPositionHasChanged, mScrollHasChanged);
					}
				}
			}

			mScrollHasChanged = false;
			return true;
		}

		private void updatePosition() {
			mTextView.getLocationInWindow(mTempCoords);

			mPositionHasChanged = mTempCoords[0] != mPositionX || mTempCoords[1] != mPositionY;

			mPositionX = mTempCoords[0];
			mPositionY = mTempCoords[1];
		}

		public void onScrollChanged() {
			mScrollHasChanged = true;
		}
	}

	private abstract class HandleView extends View implements TextViewPositionListener {
		protected Drawable mDrawable;
		protected Drawable mDrawableLtr;
		protected Drawable mDrawableRtl;
		private final PopupWindow mContainer;
		// Position with respect to the parent TextView
		private int mPositionX, mPositionY;
		private boolean mIsDragging;
		// Offset from touch position to mPosition
		private float mTouchToWindowOffsetX, mTouchToWindowOffsetY;
		protected int mHotspotX;
		// Offsets the hotspot point up, so that cursor is not hidden by the finger when moving up
		private float mTouchOffsetY;
		// Where the touch position should be on the handle to ensure a maximum cursor visibility
		private float mIdealVerticalOffset;
		// Parent's (TextView) previous position in window
		private int mLastParentX, mLastParentY;
		// Transient action popup window for Paste and Replace actions
		// protected ActionPopupWindow mActionPopupWindow;
		// Previous text character offset
		private int mPreviousOffset = -1;
		// Previous text character offset
		private boolean mPositionHasChanged = true;

		// Used to delay the appearance of the action popup window
		// private Runnable mActionPopupShower;

		public HandleView(Drawable drawableLtr, Drawable drawableRtl) {
			super(mTextView.getContext());
			mContainer = new PopupWindow(mTextView.getContext(), null, 0); // R.attr.textSelectHandleWindowStyle);
			mContainer.setSplitTouchEnabled(true);
			mContainer.setClippingEnabled(false);
			// mContainer.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
			mContainer.setContentView(this);

			mDrawableLtr = drawableLtr;
			mDrawableRtl = drawableRtl;

			updateDrawable();

			final int handleHeight = mDrawable.getIntrinsicHeight();
			mTouchOffsetY = -0.3f * handleHeight;
			mIdealVerticalOffset = 0.7f * handleHeight;
		}

		protected void updateDrawable() {
			final int offset = getCurrentCursorOffset();
			final boolean isRtlCharAtOffset = mTextView.getLayout().isRtlCharAt(offset);
			mDrawable = isRtlCharAtOffset ? mDrawableRtl : mDrawableLtr;
			mHotspotX = getHotspotX(mDrawable, isRtlCharAtOffset);
		}

		protected abstract int getHotspotX(Drawable drawable, boolean isRtlRun);

		// Touch-up filter: number of previous positions remembered
		private static final int HISTORY_SIZE = 5;
		private static final int TOUCH_UP_FILTER_DELAY_AFTER = 150;
		private static final int TOUCH_UP_FILTER_DELAY_BEFORE = 350;
		private final long[] mPreviousOffsetsTimes = new long[HISTORY_SIZE];
		private final int[] mPreviousOffsets = new int[HISTORY_SIZE];
		private int mPreviousOffsetIndex = 0;
		private int mNumberPreviousOffsets = 0;

		private void startTouchUpFilter(int offset) {
			mNumberPreviousOffsets = 0;
			addPositionToTouchUpFilter(offset);
		}

		private void addPositionToTouchUpFilter(int offset) {
			mPreviousOffsetIndex = (mPreviousOffsetIndex + 1) % HISTORY_SIZE;
			mPreviousOffsets[mPreviousOffsetIndex] = offset;
			mPreviousOffsetsTimes[mPreviousOffsetIndex] = SystemClock.uptimeMillis();
			mNumberPreviousOffsets++;
		}

		private void filterOnTouchUp() {
			final long now = SystemClock.uptimeMillis();
			int i = 0;
			int index = mPreviousOffsetIndex;
			final int iMax = Math.min(mNumberPreviousOffsets, HISTORY_SIZE);
			while (i < iMax && (now - mPreviousOffsetsTimes[index]) < TOUCH_UP_FILTER_DELAY_AFTER) {
				i++;
				index = (mPreviousOffsetIndex - i + HISTORY_SIZE) % HISTORY_SIZE;
			}

			if (i > 0 && i < iMax && (now - mPreviousOffsetsTimes[index]) > TOUCH_UP_FILTER_DELAY_BEFORE) {
				positionAtCursorOffset(mPreviousOffsets[index], false);
			}
		}

		public boolean offsetHasBeenChanged() {
			return mNumberPreviousOffsets > 1;
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			setMeasuredDimension(mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
		}

		public void show() {
			if (isShowing())
				return;

			getPositionListener().addSubscriber(this, true /* local position may change */);

			// Make sure the offset is always considered new, even when focusing at same position
			mPreviousOffset = -1;
			positionAtCursorOffset(getCurrentCursorOffset(), false);

			// hideActionPopupWindow();
		}

		protected void dismiss() {
			mIsDragging = false;
			mContainer.dismiss();
			onDetached();
		}

		public void hide() {
			dismiss();

			getPositionListener().removeSubscriber(this);
		}

		// void showActionPopupWindow(int delay) {
		// if (mActionPopupWindow == null) {
		// mActionPopupWindow = new ActionPopupWindow();
		// }
		// if (mActionPopupShower == null) {
		// mActionPopupShower = new Runnable() {
		// public void run() {
		// mActionPopupWindow.show();
		// }
		// };
		// } else {
		// mTextView.removeCallbacks(mActionPopupShower);
		// }
		// mTextView.postDelayed(mActionPopupShower, delay);
		// }

		// protected void hideActionPopupWindow() {
		// if (mActionPopupShower != null) {
		// mTextView.removeCallbacks(mActionPopupShower);
		// }
		// if (mActionPopupWindow != null) {
		// mActionPopupWindow.hide();
		// }
		// }

		public boolean isShowing() {
			return mContainer.isShowing();
		}

		private boolean isVisible() {
			// Always show a dragging handle.
			if (mIsDragging) {
				return true;
			}

			if (mTextView.isInBatchEditMode()) {
				return false;
			}

			return isPositionVisible(mPositionX + mHotspotX, mPositionY);
		}

		public abstract int getCurrentCursorOffset();

		protected abstract void updateSelection(int offset);

		public abstract void updatePosition(float x, float y);

		protected void positionAtCursorOffset(int offset, boolean parentScrolled) {
			// A HandleView relies on the layout, which may be nulled by external methods
			Layout layout = mTextView.getLayout();
			if (layout == null) {
				// Will update controllers' state, hiding them and stopping selection mode if needed
				prepareCursorControllers();
				return;
			}

			boolean offsetChanged = offset != mPreviousOffset;
			if (offsetChanged || parentScrolled) {
				if (offsetChanged) {
					updateSelection(offset);
					addPositionToTouchUpFilter(offset);
				}
				final int line = layout.getLineForOffset(offset);

				mPositionX = (int) (layout.getPrimaryHorizontal(offset) - 0.5f - mHotspotX);
				mPositionY = layout.getLineBottom(line);

				// Take TextView's padding and scroll into account.
				mPositionX += mTextView.viewportToContentHorizontalOffset();
				mPositionY += mTextView.viewportToContentVerticalOffset();

				mPreviousOffset = offset;
				mPositionHasChanged = true;
			}
		}

		public void updatePosition(int parentPositionX, int parentPositionY, boolean parentPositionChanged,
				boolean parentScrolled) {
			positionAtCursorOffset(getCurrentCursorOffset(), parentScrolled);
			if (parentPositionChanged || mPositionHasChanged) {
				if (mIsDragging) {
					// Update touchToWindow offset in case of parent scrolling while dragging
					if (parentPositionX != mLastParentX || parentPositionY != mLastParentY) {
						mTouchToWindowOffsetX += parentPositionX - mLastParentX;
						mTouchToWindowOffsetY += parentPositionY - mLastParentY;
						mLastParentX = parentPositionX;
						mLastParentY = parentPositionY;
					}

					onHandleMoved();
				}

				if (isVisible()) {
					final int positionX = parentPositionX + mPositionX;
					final int positionY = parentPositionY + mPositionY;
					if (isShowing()) {
						mContainer.update(positionX, positionY, -1, -1);
					} else {
						mContainer.showAtLocation(mTextView, Gravity.NO_GRAVITY, positionX, positionY);
					}
				} else {
					if (isShowing()) {
						dismiss();
					}
				}

				mPositionHasChanged = false;
			}
		}

		@Override
		protected void onDraw(Canvas c) {
			mDrawable.setBounds(0, 0, getRight() - getLeft(), getBottom() - getTop());
			mDrawable.draw(c);
		}

		@Override
		public boolean onTouchEvent(MotionEvent ev) {
			switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				startTouchUpFilter(getCurrentCursorOffset());
				mTouchToWindowOffsetX = ev.getRawX() - mPositionX;
				mTouchToWindowOffsetY = ev.getRawY() - mPositionY;

				final PositionListener positionListener = getPositionListener();
				mLastParentX = positionListener.getPositionX();
				mLastParentY = positionListener.getPositionY();
				mIsDragging = true;
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				final float rawX = ev.getRawX();
				final float rawY = ev.getRawY();

				// Vertical hysteresis: vertical down movement tends to snap to ideal offset
				final float previousVerticalOffset = mTouchToWindowOffsetY - mLastParentY;
				final float currentVerticalOffset = rawY - mPositionY - mLastParentY;
				float newVerticalOffset;
				if (previousVerticalOffset < mIdealVerticalOffset) {
					newVerticalOffset = Math.min(currentVerticalOffset, mIdealVerticalOffset);
					newVerticalOffset = Math.max(newVerticalOffset, previousVerticalOffset);
				} else {
					newVerticalOffset = Math.max(currentVerticalOffset, mIdealVerticalOffset);
					newVerticalOffset = Math.min(newVerticalOffset, previousVerticalOffset);
				}
				mTouchToWindowOffsetY = newVerticalOffset + mLastParentY;

				final float newPosX = rawX - mTouchToWindowOffsetX + mHotspotX;
				final float newPosY = rawY - mTouchToWindowOffsetY + mTouchOffsetY;

				updatePosition(newPosX, newPosY);
				break;
			}

			case MotionEvent.ACTION_UP:
				filterOnTouchUp();
				mIsDragging = false;
				break;

			case MotionEvent.ACTION_CANCEL:
				mIsDragging = false;
				break;
			}
			return true;
		}

		@SuppressWarnings("unused")
		public boolean isDragging() {
			return mIsDragging;
		}

		void onHandleMoved() {
			// hideActionPopupWindow();
		}

		public void onDetached() {
			// hideActionPopupWindow();
		}
	}

	private class InsertionHandleView extends HandleView {
		private static final int DELAY_BEFORE_HANDLE_FADES_OUT = 4000;
		private static final int RECENT_CUT_COPY_DURATION = 15 * 1000; // seconds

		// Used to detect taps on the insertion handle, which will affect the ActionPopupWindow
		private float mDownPositionX, mDownPositionY;
		private Runnable mHider;

		public InsertionHandleView(Drawable drawable) {
			super(drawable, drawable);
		}

		@Override
		public void show() {
			super.show();

			final long durationSinceCutOrCopy = SystemClock.uptimeMillis() - TextView.LAST_CUT_OR_COPY_TIME;
			if (durationSinceCutOrCopy < RECENT_CUT_COPY_DURATION) {
				// showActionPopupWindow(0);
			}

			hideAfterDelay();
		}

		public void showWithActionPopup() {
			show();
			// showActionPopupWindow(0);
		}

		private void hideAfterDelay() {
			if (mHider == null) {
				mHider = new Runnable() {
					public void run() {
						hide();
					}
				};
			} else {
				removeHiderCallback();
			}
			mTextView.postDelayed(mHider, DELAY_BEFORE_HANDLE_FADES_OUT);
		}

		private void removeHiderCallback() {
			if (mHider != null) {
				mTextView.removeCallbacks(mHider);
			}
		}

		@Override
		protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
			return drawable.getIntrinsicWidth() / 2;
		}

		@Override
		public boolean onTouchEvent(MotionEvent ev) {
			final boolean result = super.onTouchEvent(ev);

			switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				mDownPositionX = ev.getRawX();
				mDownPositionY = ev.getRawY();
				break;

			case MotionEvent.ACTION_UP:
				if (!offsetHasBeenChanged()) {
					final float deltaX = mDownPositionX - ev.getRawX();
					final float deltaY = mDownPositionY - ev.getRawY();
					final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

					final ViewConfiguration viewConfiguration = ViewConfiguration.get(mTextView.getContext());
					final int touchSlop = viewConfiguration.getScaledTouchSlop();

					if (distanceSquared < touchSlop * touchSlop) {
						// if (mActionPopupWindow != null && mActionPopupWindow.isShowing()) {
						// // Tapping on the handle dismisses the displayed action popup
						// mActionPopupWindow.hide();
						// } else {
						// showWithActionPopup();
						// }
					}
				}
				hideAfterDelay();
				break;

			case MotionEvent.ACTION_CANCEL:
				hideAfterDelay();
				break;

			default:
				break;
			}

			return result;
		}

		@Override
		public int getCurrentCursorOffset() {
			return mTextView.getSelectionStart();
		}

		@Override
		public void updateSelection(int offset) {
			Selection.setSelection((Spannable) mTextView.getText(), offset);
		}

		@Override
		public void updatePosition(float x, float y) {
			positionAtCursorOffset(mTextView.getOffsetForPosition(x, y), false);
		}

		@Override
		void onHandleMoved() {
			super.onHandleMoved();
			removeHiderCallback();
		}

		@Override
		public void onDetached() {
			super.onDetached();
			removeHiderCallback();
		}
	}

	/**
	 * A CursorController instance can be used to control a cursor in the text.
	 */
	private interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {
		/**
		 * Makes the cursor controller visible on screen. See also {@link #hide()}.
		 */
		public void show();

		/**
		 * Hide the cursor controller from screen. See also {@link #show()}.
		 */
		public void hide();

		/**
		 * Called when the view is detached from window. Perform house keeping task, such as stopping Runnable thread
		 * that would otherwise keep a reference on the context, thus preventing the activity from being recycled.
		 */
		public void onDetached();
	}

	private class InsertionPointCursorController implements CursorController {
		private InsertionHandleView mHandle;

		public void show() {
			getHandle().show();
		}

		public void showWithActionPopup() {
			getHandle().showWithActionPopup();
		}

		public void hide() {
			if (mHandle != null) {
				mHandle.hide();
			}
		}

		public void onTouchModeChanged(boolean isInTouchMode) {
			if (!isInTouchMode) {
				hide();
			}
		}

		private InsertionHandleView getHandle() {
			if (mSelectHandleCenter == null) {
				mSelectHandleCenter = mTextView.getResources().getDrawable(mTextView.mTextSelectHandleRes);
			}
			if (mHandle == null) {
				mHandle = new InsertionHandleView(mSelectHandleCenter);
			}
			return mHandle;
		}

		@Override
		public void onDetached() {
			final ViewTreeObserver observer = mTextView.getViewTreeObserver();
			observer.removeOnTouchModeChangeListener(this);

			if (mHandle != null)
				mHandle.onDetached();
		}
	}

	static class InputContentType {
		int imeOptions = EditorInfo.IME_NULL;
		String privateImeOptions;
		CharSequence imeActionLabel;
		int imeActionId;
		Bundle extras;
		OnEditorActionListener onEditorActionListener;
		boolean enterDown;
	}

	static class InputMethodState {
		Rect mCursorRectInWindow = new Rect();
		RectF mTmpRectF = new RectF();
		float[] mTmpOffset = new float[2];
		ExtractedTextRequest mExtractedTextRequest;
		final ExtractedText mExtractedText = new ExtractedText();
		int mBatchEditNesting;
		boolean mCursorChanged;
		boolean mSelectionModeChanged;
		boolean mContentChanged;
		int mChangedStart, mChangedEnd, mChangedDelta;
	}

}
