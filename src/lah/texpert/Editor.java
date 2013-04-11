package lah.texpert;

import lah.texpert.TextView.OnEditorActionListener;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.KeyListener;
import android.text.style.EasyEditSpan;
import android.text.style.SuggestionSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupWindow;

/**
 * Helper class used by TextView to handle editable text views.
 * 
 * @hide
 */
@SuppressLint("NewApi")
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class Editor {
	@SuppressWarnings("unused")
	private static final String TAG = "Editor";

	static final int BLINK = 500;
	private static final float[] TEMP_POSITION = new float[2];
	// private static int DRAG_SHADOW_MAX_TEXT_LENGTH = 20;

	// Cursor Controllers.
	InsertionPointCursorController mInsertionPointCursorController;
	SelectionModifierCursorController mSelectionModifierCursorController;
	ActionMode mSelectionActionMode;
	boolean mInsertionControllerEnabled;
	boolean mSelectionControllerEnabled;

	// Used to highlight a word when it is corrected by the IME
	CorrectionHighlighter mCorrectionHighlighter;

	InputContentType mInputContentType;
	InputMethodState mInputMethodState;

	// DisplayList[] mTextDisplayLists;
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

	// CharSequence mError;
	// boolean mErrorWasChanged;
	// ErrorPopup mErrorPopup;

	// /**
	// * This flag is set if the TextView tries to display an error before it is attached to the window (so its position
	// * is still unknown). It causes the error to be shown later, when onAttachedToWindow() is called.
	// */
	// boolean mShowErrorAfterAttach;

	boolean mInBatchEditControllers;
	boolean mShowSoftInputOnFocus = true;
	boolean mPreserveDetachedSelection;
	boolean mTemporaryDetach;

	// SuggestionsPopupWindow mSuggestionsPopupWindow;
	// SuggestionRangeSpan mSuggestionRangeSpan;
	// Runnable mShowSuggestionRunnable;

	final Drawable[] mCursorDrawable = new Drawable[2];
	int mCursorCount; // Current number of used mCursorDrawable: 0 (resource=0), 1 or 2 (split)

	private Drawable mSelectHandleLeft;
	private Drawable mSelectHandleRight;
	private Drawable mSelectHandleCenter;

	// Global listener that detects changes in the global position of the TextView
	private PositionListener mPositionListener;

	float mLastDownPositionX, mLastDownPositionY;
	Callback mCustomSelectionActionModeCallback;

	// Set when this TextView gained focus with some text selected. Will start selection mode.
	boolean mCreatedWithASelection;

	private EasyEditSpanController mEasyEditSpanController;

	// WordIterator mWordIterator;

	private Rect mTempRect;

	private TextView mTextView;

	// private final UserDictionaryListener mUserDictionaryListener = new UserDictionaryListener();

	Editor(TextView textView) {
		mTextView = textView;
	}

	void onAttachedToWindow() {
		// if (mShowErrorAfterAttach) {
		// showError();
		// mShowErrorAfterAttach = false;
		// }
		mTemporaryDetach = false;

		final ViewTreeObserver observer = mTextView.getViewTreeObserver();
		// No need to create the controller.
		// The get method will add the listener on controller creation.
		if (mInsertionPointCursorController != null) {
			observer.addOnTouchModeChangeListener(mInsertionPointCursorController);
		}
		if (mSelectionModifierCursorController != null) {
			mSelectionModifierCursorController.resetTouchOffsets();
			observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
		}
		// updateSpellCheckSpans(0, mTextView.getText().length(), true /* create the spell checker if needed */);

		if (mTextView.hasTransientState() && mTextView.getSelectionStart() != mTextView.getSelectionEnd()) {
			// Since transient state is reference counted make sure it stays matched
			// with our own calls to it for managing selection.
			// The action mode callback will set this back again when/if the action mode starts.
			mTextView.setHasTransientState(false);

			// We had an active selection from before, start the selection mode.
			startSelectionActionMode();
		}
	}

	void onDetachedFromWindow() {
		// if (null != null) {
		// hideError();
		// }

		if (mBlink != null) {
			mBlink.removeCallbacks(mBlink);
		}

		if (mInsertionPointCursorController != null) {
			mInsertionPointCursorController.onDetached();
		}

		if (mSelectionModifierCursorController != null) {
			mSelectionModifierCursorController.onDetached();
		}

		// if (mShowSuggestionRunnable != null) {
		// mTextView.removeCallbacks(mShowSuggestionRunnable);
		// }

		// invalidateTextDisplayList();
		//
		// if (null != null) {
		// null.closeSession();
		// // Forces the creation of a new SpellChecker next time this window is created.
		// // Will handle the cases where the settings has been changed in the meantime.
		// null = null;
		// }

		mPreserveDetachedSelection = true;
		hideControllers();
		mPreserveDetachedSelection = false;
		mTemporaryDetach = false;
	}

	void createInputContentTypeIfNeeded() {
		if (mInputContentType == null) {
			mInputContentType = new InputContentType();
		}
	}

	void createInputMethodStateIfNeeded() {
		if (mInputMethodState == null) {
			mInputMethodState = new InputMethodState();
		}
	}

	boolean isCursorVisible() {
		// The default value is true, even when there is no associated Editor
		return mCursorVisible && mTextView.isTextEditable();
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
		mSelectionControllerEnabled = enabled && mTextView.textCanBeSelected();

		if (!mInsertionControllerEnabled) {
			hideInsertionPointCursorController();
			if (mInsertionPointCursorController != null) {
				mInsertionPointCursorController.onDetached();
				mInsertionPointCursorController = null;
			}
		}

		if (!mSelectionControllerEnabled) {
			stopSelectionActionMode();
			if (mSelectionModifierCursorController != null) {
				mSelectionModifierCursorController.onDetached();
				mSelectionModifierCursorController = null;
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
		hideSpanControllers();
	}

	private void hideSpanControllers() {
		if (mEasyEditSpanController != null) {
			mEasyEditSpanController.hide();
		}
	}

	private void hideCursorControllers() {
		// if (mSuggestionsPopupWindow != null && !mSuggestionsPopupWindow.isShowingUp()) {
		// // Should be done before hide insertion point controller since it triggers a show of it
		// mSuggestionsPopupWindow.hide();
		// }
		hideInsertionPointCursorController();
		stopSelectionActionMode();
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

	void adjustInputType(boolean password, boolean passwordInputType, boolean webPasswordInputType,
			boolean numberPasswordInputType) {
		// mInputType has been set from inputType, possibly modified by mInputMethod.
		// Specialize mInputType to [web]password if we have a text class and the original input
		// type was a password.
		if ((mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
			if (password || passwordInputType) {
				mInputType = (mInputType & ~(EditorInfo.TYPE_MASK_VARIATION)) | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
			}
			if (webPasswordInputType) {
				mInputType = (mInputType & ~(EditorInfo.TYPE_MASK_VARIATION))
						| EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD;
			}
		} else if ((mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_NUMBER) {
			if (numberPasswordInputType) {
				mInputType = (mInputType & ~(EditorInfo.TYPE_MASK_VARIATION))
						| EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD;
			}
		}
	}

	// private void chooseSize(PopupWindow pop, CharSequence text, TextView tv) {
	// int wid = tv.getPaddingLeft() + tv.getPaddingRight();
	// int ht = tv.getPaddingTop() + tv.getPaddingBottom();
	//
	// int defaultWidthInPixels = 10;
	// // mTextView.getResources().getDimensionPixelSize(
	// // com.android.internal.R.dimen.textview_error_popup_default_width);
	// Layout l = new StaticLayout(text, tv.getPaint(), defaultWidthInPixels, Layout.Alignment.ALIGN_NORMAL, 1, 0,
	// true);
	// float max = 0;
	// for (int i = 0; i < l.getLineCount(); i++) {
	// max = Math.max(max, l.getLineWidth(i));
	// }
	//
	// /*
	// * Now set the popup size to be big enough for the text plus the border capped to DEFAULT_MAX_POPUP_WIDTH
	// */
	// pop.setWidth(wid + (int) Math.ceil(max));
	// pop.setHeight(ht + l.getHeight());
	// }

	void setFrame() {
		// if (mErrorPopup != null) {
		// TextView tv = (TextView) mErrorPopup.getContentView();
		// chooseSize(mErrorPopup, null, tv);
		// mErrorPopup.update(mTextView, getErrorX(), getErrorY(), mErrorPopup.getWidth(), mErrorPopup.getHeight());
		// }
	}

	/**
	 * Unlike {@link TextView#textCanBeSelected()}, this method is based on the <i>current</i> state of the TextView.
	 * textCanBeSelected() has to be true (this is one of the conditions to have a selection controller (see
	 * {@link #prepareCursorControllers()}), but this is not sufficient.
	 */
	private boolean canSelectText() {
		return hasSelectionController() && mTextView.getText().length() != 0;
	}

	// /**
	// * It would be better to rely on the input type for everything. A password inputType should have a password
	// * transformation. We should hence use isPasswordInputType instead of this method.
	// *
	// * We should: - Call setInputType in setKeyListener instead of changing the input type directly (which would
	// install
	// * the correct transformation). - Refuse the installation of a non-password transformation in setTransformation if
	// * the input type is password.
	// *
	// * However, this is like this for legacy reasons and we cannot break existing apps. This method is useful since it
	// * matches what the user can see (obfuscated text or not).
	// *
	// * @return true if the current transformation method is of the password type.
	// */
	// private boolean hasPasswordTransformationMethod() {
	// return mTextView.getTransformationMethod() instanceof PasswordTransformationMethod;
	// }

	/**
	 * Adjusts selection to the word under last touch offset. Return true if the operation was successfully performed.
	 */
	private boolean selectCurrentWord() {
		if (!canSelectText()) {
			return false;
		}
		long lastTouchOffsets = getLastTouchOffsets();
		final int minOffset = TextUtils.unpackRangeStartFromLong(lastTouchOffsets);
		final int maxOffset = TextUtils.unpackRangeEndFromLong(lastTouchOffsets);
		// Safety check in case standard touch event handling has been bypassed
		if (minOffset < 0 || minOffset >= mTextView.getText().length())
			return false;
		if (maxOffset < 0 || maxOffset >= mTextView.getText().length())
			return false;
		return false;
	}

	void onLocaleChanged() {
		// Will be re-created on demand in getWordIterator with the proper new locale
		// mWordIterator = null;
	}

	// /**
	// * @hide
	// */
	// public WordIterator getWordIterator() {
	// if (mWordIterator == null) {
	// mWordIterator = new WordIterator(mTextView.getTextServicesLocale());
	// }
	// return mWordIterator;
	// }

	// private long getCharRange(int offset) {
	// final int textLength = mTextView.getText().length();
	// if (offset + 1 < textLength) {
	// final char currentChar = mTextView.getText().charAt(offset);
	// final char nextChar = mTextView.getText().charAt(offset + 1);
	// if (Character.isSurrogatePair(currentChar, nextChar)) {
	// return TextUtils.packRangeInLong(offset, offset + 2);
	// }
	// }
	// if (offset < textLength) {
	// return TextUtils.packRangeInLong(offset, offset + 1);
	// }
	// if (offset - 2 >= 0) {
	// final char previousChar = mTextView.getText().charAt(offset - 1);
	// final char previousPreviousChar = mTextView.getText().charAt(offset - 2);
	// if (Character.isSurrogatePair(previousPreviousChar, previousChar)) {
	// return TextUtils.packRangeInLong(offset - 2, offset);
	// }
	// }
	// if (offset - 1 >= 0) {
	// return TextUtils.packRangeInLong(offset - 1, offset);
	// }
	// return TextUtils.packRangeInLong(offset, offset);
	// }

	private boolean touchPositionIsInSelection() {
		int selectionStart = mTextView.getSelectionStart();
		int selectionEnd = mTextView.getSelectionEnd();

		if (selectionStart == selectionEnd) {
			return false;
		}

		if (selectionStart > selectionEnd) {
			int tmp = selectionStart;
			selectionStart = selectionEnd;
			selectionEnd = tmp;
			Selection.setSelection((Spannable) mTextView.getText(), selectionStart, selectionEnd);
		}

		SelectionModifierCursorController selectionController = getSelectionController();
		int minOffset = selectionController.getMinTouchOffset();
		int maxOffset = selectionController.getMaxTouchOffset();

		return ((minOffset >= selectionStart) && (maxOffset < selectionEnd));
	}

	private PositionListener getPositionListener() {
		if (mPositionListener == null) {
			mPositionListener = new PositionListener();
		}
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

	private boolean isOffsetVisible(int offset) {
		Layout layout = mTextView.getLayout();
		final int line = layout.getLineForOffset(offset);
		final int lineBottom = layout.getLineBottom(line);
		final int primaryHorizontal = (int) layout.getPrimaryHorizontal(offset);
		return isPositionVisible(primaryHorizontal + mTextView.viewportToContentHorizontalOffset(), lineBottom
				+ mTextView.viewportToContentVerticalOffset());
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
			stopSelectionActionMode();
			Selection.setSelection((Spannable) mTextView.getText(), offset);
			getInsertionController().showWithActionPopup();
			handled = true;
		}

		if (!handled && mSelectionActionMode != null) {
			if (touchPositionIsInSelection()) {
				// Start a drag
				final int start = mTextView.getSelectionStart();
				final int end = mTextView.getSelectionEnd();
				CharSequence selectedText = mTextView.getTransformedText(start, end);
				ClipData data = ClipData.newPlainText(null, selectedText);
				DragLocalState localState = new DragLocalState(mTextView, start, end);
				mTextView.startDrag(data, getTextThumbnailBuilder(selectedText), localState, 0);
				stopSelectionActionMode();
			} else {
				getSelectionController().hide();
				selectCurrentWord();
				getSelectionController().show();
			}
			handled = true;
		}

		// Start a new selection
		if (!handled) {
			handled = startSelectionActionMode();
		}

		return handled;
	}

	private long getLastTouchOffsets() {
		SelectionModifierCursorController selectionController = getSelectionController();
		final int minOffset = selectionController.getMinTouchOffset();
		final int maxOffset = selectionController.getMaxTouchOffset();
		return TextUtils.packRangeInLong(minOffset, maxOffset);
	}

	void onFocusChanged(boolean focused, int direction) {
		mShowCursor = SystemClock.uptimeMillis();
		ensureEndedBatchEdit();

		if (focused) {
			int selStart = mTextView.getSelectionStart();
			int selEnd = mTextView.getSelectionEnd();

			// SelectAllOnFocus fields are highlighted and not selected. Do not start text selection
			// mode for these, unless there was a specific selection already started.
			final boolean isFocusHighlighted = mSelectAllOnFocus && selStart == 0
					&& selEnd == mTextView.getText().length();

			mCreatedWithASelection = mFrozenWithFocus && mTextView.hasSelection() && !isFocusHighlighted;

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

			// if (null != null) {
			// showError();
			// }

			makeBlink();
		} else {
			// if (null != null) {
			// hideError();
			// }
			// Don't leave us in the middle of a batch edit.
			mTextView.onEndBatchEdit();

			// if (mTextView instanceof ExtractEditText) {
			// // terminateTextSelectionMode removes selection, which we want to keep when
			// // ExtractEditText goes out of focus.
			// final int selStart = mTextView.getSelectionStart();
			// final int selEnd = mTextView.getSelectionEnd();
			// hideControllers();
			// Selection.setSelection((Spannable) mTextView.getText(), selStart, selEnd);
			// } else {
			if (mTemporaryDetach)
				mPreserveDetachedSelection = true;
			hideControllers();
			if (mTemporaryDetach)
				mPreserveDetachedSelection = false;
			downgradeEasyCorrectionSpans();
			// }

			// No need to create the controller
			if (mSelectionModifierCursorController != null) {
				mSelectionModifierCursorController.resetTouchOffsets();
			}
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
		// updateSpellCheckSpans(start, start + after, false);

		// Hide the controllers as soon as text is modified (typing, procedural...)
		// We do not hide the span controllers, since they can be added when a new text is
		// inserted into the text view (voice IME).
		hideCursorControllers();
	}

	private int getLastTapPosition() {
		// No need to create the controller at that point, no last tap position saved
		if (mSelectionModifierCursorController != null) {
			int lastTapPosition = mSelectionModifierCursorController.getMinTouchOffset();
			if (lastTapPosition >= 0) {
				// Safety check, should not be possible.
				if (lastTapPosition > mTextView.getText().length()) {
					lastTapPosition = mTextView.getText().length();
				}
				return lastTapPosition;
			}
		}

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
			// if (mSuggestionsPopupWindow != null) {
			// mSuggestionsPopupWindow.onParentLostFocus();
			// }

			// Don't leave us in the middle of a batch edit. Same as in onFocusChanged
			ensureEndedBatchEdit();
		}
	}

	void onTouchEvent(MotionEvent event) {
		if (hasSelectionController()) {
			getSelectionController().onTouchEvent(event);
		}

		// if (mShowSuggestionRunnable != null) {
		// mTextView.removeCallbacks(mShowSuggestionRunnable);
		// mShowSuggestionRunnable = null;
		// }

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
			if (mTextView.isSingleLine()) {
				outText.flags |= ExtractedText.FLAG_SINGLE_LINE;
			}
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

		if (mCorrectionHighlighter != null) {
			mCorrectionHighlighter.draw(canvas, cursorOffsetVertical);
		}

		if (highlight != null && selectionStart == selectionEnd && mCursorCount > 0) {
			drawCursor(canvas, cursorOffsetVertical);
			// Rely on the drawable entirely, do not draw the cursor line.
			// Has to be done after the IMM related code above which relies on the highlight.
			highlight = null;
		}

		// if (mTextView.canHaveDisplayList() && canvas.isHardwareAccelerated()) {
		// drawHardwareAccelerated(canvas, layout, highlight, highlightPaint, cursorOffsetVertical);
		// } else {
		layout.draw(canvas, highlight, highlightPaint, cursorOffsetVertical);
		// }
	}

	// private void drawHardwareAccelerated(Canvas canvas, Layout layout, Path highlight, Paint highlightPaint,
	// int cursorOffsetVertical) {
	// final long lineRange = layout.getLineRangeForDraw(canvas);
	// int firstLine = TextUtils.unpackRangeStartFromLong(lineRange);
	// int lastLine = TextUtils.unpackRangeEndFromLong(lineRange);
	// if (lastLine < 0)
	// return;
	//
	// layout.drawBackground(canvas, highlight, highlightPaint, cursorOffsetVertical, firstLine, lastLine);
	//
	// if (layout instanceof DynamicLayout) {
	// if (mTextDisplayLists == null) {
	// mTextDisplayLists = new DisplayList[ArrayUtils.idealObjectArraySize(0)];
	// }
	//
	// // If the height of the layout changes (usually when inserting or deleting a line,
	// // but could be changes within a span), invalidate everything. We could optimize
	// // more aggressively (for example, adding offsets to blocks) but it would be more
	// // complex and we would only get the benefit in some cases.
	// int layoutHeight = layout.getHeight();
	// if (mLastLayoutHeight != layoutHeight) {
	// invalidateTextDisplayList();
	// mLastLayoutHeight = layoutHeight;
	// }
	//
	// DynamicLayout dynamicLayout = (DynamicLayout) layout;
	// int[] blockEndLines = dynamicLayout.getBlockEndLines();
	// int[] blockIndices = dynamicLayout.getBlockIndices();
	// final int numberOfBlocks = dynamicLayout.getNumberOfBlocks();
	//
	// int endOfPreviousBlock = -1;
	// int searchStartIndex = 0;
	// for (int i = 0; i < numberOfBlocks; i++) {
	// int blockEndLine = blockEndLines[i];
	// int blockIndex = blockIndices[i];
	//
	// final boolean blockIsInvalid = blockIndex == DynamicLayout.INVALID_BLOCK_INDEX;
	// if (blockIsInvalid) {
	// blockIndex = getAvailableDisplayListIndex(blockIndices, numberOfBlocks, searchStartIndex);
	// // Note how dynamic layout's internal block indices get updated from Editor
	// blockIndices[i] = blockIndex;
	// searchStartIndex = blockIndex + 1;
	// }
	//
	// DisplayList blockDisplayList = mTextDisplayLists[blockIndex];
	// if (blockDisplayList == null) {
	// blockDisplayList = mTextDisplayLists[blockIndex] = mTextView.getHardwareRenderer()
	// .createDisplayList("Text " + blockIndex);
	// } else {
	// if (blockIsInvalid)
	// blockDisplayList.invalidate();
	// }
	//
	// if (!blockDisplayList.isValid()) {
	// final int blockBeginLine = endOfPreviousBlock + 1;
	// final int top = layout.getLineTop(blockBeginLine);
	// final int bottom = layout.getLineBottom(blockEndLine);
	// int left = 0;
	// int right = mTextView.getWidth();
	// if (mTextView.getHorizontallyScrolling()) {
	// float min = Float.MAX_VALUE;
	// float max = Float.MIN_VALUE;
	// for (int line = blockBeginLine; line <= blockEndLine; line++) {
	// min = Math.min(min, layout.getLineLeft(line));
	// max = Math.max(max, layout.getLineRight(line));
	// }
	// left = (int) min;
	// right = (int) (max + 0.5f);
	// }
	//
	// final HardwareCanvas hardwareCanvas = blockDisplayList.start();
	// try {
	// // Tighten the bounds of the viewport to the actual text size
	// hardwareCanvas.setViewport(right - left, bottom - top);
	// // The dirty rect should always be null for a display list
	// hardwareCanvas.onPreDraw(null);
	// // drawText is always relative to TextView's origin, this translation brings
	// // this range of text back to the top left corner of the viewport
	// hardwareCanvas.translate(-left, -top);
	// layout.drawText(hardwareCanvas, blockBeginLine, blockEndLine);
	// // No need to untranslate, previous context is popped after drawDisplayList
	// } finally {
	// hardwareCanvas.onPostDraw();
	// blockDisplayList.end();
	// blockDisplayList.setLeftTopRightBottom(left, top, right, bottom);
	// // Same as drawDisplayList below, handled by our TextView's parent
	// blockDisplayList.setClipChildren(false);
	// }
	// }
	//
	// ((HardwareCanvas) canvas).drawDisplayList(blockDisplayList, null, 0 /*
	// * no child clipping, our TextView
	// * parent enforces it
	// */);
	//
	// endOfPreviousBlock = blockEndLine;
	// }
	// } else {
	// // Boring layout is used for empty and hint text
	// layout.drawText(canvas, firstLine, lastLine);
	// }
	// }

	// private int getAvailableDisplayListIndex(int[] blockIndices, int numberOfBlocks, int searchStartIndex) {
	// int length = mTextDisplayLists.length;
	// for (int i = searchStartIndex; i < length; i++) {
	// boolean blockIndexFound = false;
	// for (int j = 0; j < numberOfBlocks; j++) {
	// if (blockIndices[j] == i) {
	// blockIndexFound = true;
	// break;
	// }
	// }
	// if (blockIndexFound)
	// continue;
	// return i;
	// }
	//
	// // No available index found, the pool has to grow
	// int newSize = ArrayUtils.idealIntArraySize(length + 1);
	// DisplayList[] displayLists = new DisplayList[newSize];
	// System.arraycopy(mTextDisplayLists, 0, displayLists, 0, length);
	// mTextDisplayLists = displayLists;
	// return length;
	// }

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

	// /**
	// * Invalidates all the sub-display lists that overlap the specified character range
	// */
	// void invalidateTextDisplayList(Layout layout, int start, int end) {
	// if (mTextDisplayLists != null && layout instanceof DynamicLayout) {
	// final int firstLine = layout.getLineForOffset(start);
	// final int lastLine = layout.getLineForOffset(end);
	//
	// DynamicLayout dynamicLayout = (DynamicLayout) layout;
	// int[] blockEndLines = dynamicLayout.getBlockEndLines();
	// int[] blockIndices = dynamicLayout.getBlockIndices();
	// final int numberOfBlocks = dynamicLayout.getNumberOfBlocks();
	//
	// int i = 0;
	// // Skip the blocks before firstLine
	// while (i < numberOfBlocks) {
	// if (blockEndLines[i] >= firstLine)
	// break;
	// i++;
	// }
	//
	// // Invalidate all subsequent blocks until lastLine is passed
	// while (i < numberOfBlocks) {
	// final int blockIndex = blockIndices[i];
	// if (blockIndex != DynamicLayout.INVALID_BLOCK_INDEX) {
	// mTextDisplayLists[blockIndex].invalidate();
	// }
	// if (blockEndLines[i] >= lastLine)
	// break;
	// i++;
	// }
	// }
	// }

	// void invalidateTextDisplayList() {
	// if (mTextDisplayLists != null) {
	// for (int i = 0; i < mTextDisplayLists.length; i++) {
	// if (mTextDisplayLists[i] != null)
	// mTextDisplayLists[i].invalidate();
	// }
	// }
	// }

	void updateCursorsPositions() {
		if (mTextView.mCursorDrawableRes == 0) {
			mCursorCount = 0;
			return;
		}

		Layout layout = mTextView.getLayout();
		// Layout hintLayout = mTextView.getHintLayout();
		final int offset = mTextView.getSelectionStart();
		final int line = layout.getLineForOffset(offset);
		final int top = layout.getLineTop(line);
		final int bottom = layout.getLineTop(line + 1);

		// mCursorCount = layout.isLevelBoundary(offset) ? 2 : 1;

		int middle = bottom;
		if (mCursorCount == 2) {
			// Similar to what is done in {@link Layout.#getCursorPath(int, Path, CharSequence)}
			middle = (top + bottom) >> 1;
		}

		// updateCursorPosition(0, top, middle, getPrimaryHorizontal(layout, hintLayout, offset));

		if (mCursorCount == 2) {
			updateCursorPosition(1, middle, bottom, layout.getSecondaryHorizontal(offset));
		}
	}

	// private float getPrimaryHorizontal(Layout layout, Layout hintLayout, int offset) {
	// if (TextUtils.isEmpty(layout.getText()) && hintLayout != null && !TextUtils.isEmpty(hintLayout.getText())) {
	// return hintLayout.getPrimaryHorizontal(offset);
	// } else {
	// return layout.getPrimaryHorizontal(offset);
	// }
	// }

	/**
	 * @return true if the selection mode was actually started.
	 */
	boolean startSelectionActionMode() {
		if (mSelectionActionMode != null) {
			// Selection action mode is already started
			return false;
		}

		if (!canSelectText() || !mTextView.requestFocus()) {
			Log.w(TextView.LOG_TAG, "TextView does not support text selection. Action mode cancelled.");
			return false;
		}

		if (!mTextView.hasSelection()) {
			// There may already be a selection on device rotation
			if (!selectCurrentWord()) {
				// No word found under cursor or text selection not permitted.
				return false;
			}
		}

		boolean willExtract = extractedTextModeWillBeStarted();

		// Do not start the action mode when extracted text will show up full screen, which would
		// immediately hide the newly created action bar and would be visually distracting.
		if (!willExtract) {
			ActionMode.Callback actionModeCallback = new SelectionActionModeCallback();
			mSelectionActionMode = mTextView.startActionMode(actionModeCallback);
		}

		final boolean selectionStarted = mSelectionActionMode != null || willExtract;
		if (selectionStarted && !mTextView.isTextSelectable() && mShowSoftInputOnFocus) {
			// Show the IME to be able to replace text, except when selecting non editable text.
			final InputMethodManager imm = (InputMethodManager) mTextView.getContext().getSystemService(
					Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.showSoftInput(mTextView, 0, null);
			}
		}

		return selectionStarted;
	}

	private boolean extractedTextModeWillBeStarted() {
		// if (!(mTextView instanceof ExtractEditText)) {
		// final InputMethodManager imm = (InputMethodManager) mTextView.getContext().getSystemService(
		// Context.INPUT_METHOD_SERVICE);
		// return imm != null && imm.isFullscreenMode();
		// }
		return false;
	}

	/**
	 * @return <code>true</code> if the cursor/current selection overlaps a {@link SuggestionSpan}.
	 */
	private boolean isCursorInsideSuggestionSpan() {
		CharSequence text = mTextView.getText();
		if (!(text instanceof Spannable))
			return false;

		SuggestionSpan[] suggestionSpans = ((Spannable) text).getSpans(mTextView.getSelectionStart(),
				mTextView.getSelectionEnd(), SuggestionSpan.class);
		return (suggestionSpans.length > 0);
	}

	/**
	 * @return <code>true</code> if the cursor is inside an {@link SuggestionSpan} with
	 *         {@link SuggestionSpan#FLAG_EASY_CORRECT} set.
	 */
	private boolean isCursorInsideEasyCorrectionSpan() {
		Spannable spannable = (Spannable) mTextView.getText();
		SuggestionSpan[] suggestionSpans = spannable.getSpans(mTextView.getSelectionStart(),
				mTextView.getSelectionEnd(), SuggestionSpan.class);
		for (int i = 0; i < suggestionSpans.length; i++) {
			if ((suggestionSpans[i].getFlags() & SuggestionSpan.FLAG_EASY_CORRECT) != 0) {
				return true;
			}
		}
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
			// if (null != null) {
			// // When the cursor moves, the word that was typed may need spell check
			// null.onSelectionChanged();
			// }
			if (!extractedTextModeWillBeStarted()) {
				if (isCursorInsideEasyCorrectionSpan()) {
					// mShowSuggestionRunnable = new Runnable() {
					// public void run() {
					// showSuggestions();
					// }
					// };
					// // removeCallbacks is performed on every touch
					// mTextView.postDelayed(mShowSuggestionRunnable, ViewConfiguration.getDoubleTapTimeout());
				} else if (hasInsertionController()) {
					getInsertionController().show();
				}
			}
		}
	}

	protected void stopSelectionActionMode() {
		if (mSelectionActionMode != null) {
			// This will hide the mSelectionModifierCursorController
			mSelectionActionMode.finish();
		}
	}

	/**
	 * @return True if this view supports insertion handles.
	 */
	boolean hasInsertionController() {
		return mInsertionControllerEnabled;
	}

	/**
	 * @return True if this view supports selection handles.
	 */
	boolean hasSelectionController() {
		return mSelectionControllerEnabled;
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

	SelectionModifierCursorController getSelectionController() {
		if (!mSelectionControllerEnabled) {
			return null;
		}

		if (mSelectionModifierCursorController == null) {
			mSelectionModifierCursorController = new SelectionModifierCursorController();

			final ViewTreeObserver observer = mTextView.getViewTreeObserver();
			observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
		}

		return mSelectionModifierCursorController;
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

	/**
	 * Called by the framework in response to a text auto-correction (such as fixing a typo using a a dictionnary) from
	 * the current input method, provided by it calling {@link InputConnection#commitCorrection}
	 * InputConnection.commitCorrection()}. The default implementation flashes the background of the corrected word to
	 * provide feedback to the user.
	 * 
	 * @param info
	 *            The auto correct info about the text that was corrected.
	 */
	public void onCommitCorrection(CorrectionInfo info) {
		if (mCorrectionHighlighter == null) {
			mCorrectionHighlighter = new CorrectionHighlighter();
		} else {
			mCorrectionHighlighter.invalidate(false);
		}

		mCorrectionHighlighter.highlight(info);
	}

	// void showSuggestions() {
	// if (mSuggestionsPopupWindow == null) {
	// mSuggestionsPopupWindow = new SuggestionsPopupWindow();
	// }
	// hideControllers();
	// mSuggestionsPopupWindow.show();
	// }

	// boolean areSuggestionsShown() {
	// return mSuggestionsPopupWindow != null && mSuggestionsPopupWindow.isShowing();
	// }

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

	private DragShadowBuilder getTextThumbnailBuilder(CharSequence text) {
		// TextView shadowView = (TextView) View.inflate(mTextView.getContext(),
		// com.android.internal.R.layout.text_drag_thumbnail, null);

		// if (shadowView == null) {
		// throw new IllegalArgumentException("Unable to inflate text drag thumbnail");
		// }
		//
		// if (text.length() > DRAG_SHADOW_MAX_TEXT_LENGTH) {
		// text = text.subSequence(0, DRAG_SHADOW_MAX_TEXT_LENGTH);
		// }
		// shadowView.setText(text);
		// shadowView.setTextColor(mTextView.getTextColors());

		// shadowView.setTextAppearance(mTextView.getContext(), R.styleable.Theme_textAppearanceLarge);
		// shadowView.setGravity(Gravity.CENTER);

		// shadowView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
		// ViewGroup.LayoutParams.WRAP_CONTENT));
		//
		// final int size = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		// shadowView.measure(size, size);
		//
		// shadowView.layout(0, 0, shadowView.getMeasuredWidth(), shadowView.getMeasuredHeight());
		// shadowView.invalidate();
		// return new DragShadowBuilder(shadowView);
		return null;
	}

	private static class DragLocalState {
		public TextView sourceTextView;
		public int start, end;

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

		if (mEasyEditSpanController == null) {
			mEasyEditSpanController = new EasyEditSpanController();
		}
		text.setSpan(mEasyEditSpanController, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
	}

	/**
	 * Controls the {@link EasyEditSpan} monitoring when it is added, and when the related pop-up should be displayed.
	 */
	class EasyEditSpanController implements SpanWatcher {

		private static final int DISPLAY_TIMEOUT_MS = 3000; // 3 secs

		private EasyEditPopupWindow mPopupWindow;

		private Runnable mHidePopup;

		@Override
		public void onSpanAdded(Spannable text, Object span, int start, int end) {
			if (span instanceof EasyEditSpan) {
				if (mPopupWindow == null) {
					mPopupWindow = new EasyEditPopupWindow();
					mHidePopup = new Runnable() {
						@Override
						public void run() {
							hide();
						}
					};
				}

				// Make sure there is only at most one EasyEditSpan in the text
				if (mPopupWindow.mEasyEditSpan != null) {
					text.removeSpan(mPopupWindow.mEasyEditSpan);
				}

				mPopupWindow.setEasyEditSpan((EasyEditSpan) span);

				if (mTextView.getWindowVisibility() != View.VISIBLE) {
					// The window is not visible yet, ignore the text change.
					return;
				}

				if (mTextView.getLayout() == null) {
					// The view has not been laid out yet, ignore the text change
					return;
				}

				if (extractedTextModeWillBeStarted()) {
					// The input is in extract mode. Do not handle the easy edit in
					// the original TextView, as the ExtractEditText will do
					return;
				}

				mPopupWindow.show();
				mTextView.removeCallbacks(mHidePopup);
				mTextView.postDelayed(mHidePopup, DISPLAY_TIMEOUT_MS);
			}
		}

		@Override
		public void onSpanRemoved(Spannable text, Object span, int start, int end) {
			if (mPopupWindow != null && span == mPopupWindow.mEasyEditSpan) {
				hide();
			}
		}

		@Override
		public void onSpanChanged(Spannable text, Object span, int previousStart, int previousEnd, int newStart,
				int newEnd) {
			if (mPopupWindow != null && span == mPopupWindow.mEasyEditSpan) {
				text.removeSpan(mPopupWindow.mEasyEditSpan);
			}
		}

		public void hide() {
			if (mPopupWindow != null) {
				mPopupWindow.hide();
				mTextView.removeCallbacks(mHidePopup);
			}
		}
	}

	/**
	 * Displays the actions associated to an {@link EasyEditSpan}. The pop-up is controlled by
	 * {@link EasyEditSpanController}.
	 */
	private class EasyEditPopupWindow extends PinnedPopupWindow implements OnClickListener {
		// private static final int POPUP_TEXT_LAYOUT = com.android.internal.R.layout.text_edit_action_popup_text;
		private TextView mDeleteTextView;
		private EasyEditSpan mEasyEditSpan;

		@Override
		protected void createPopupWindow() {
			// mPopupWindow = new PopupWindow(mTextView.getContext(), null,
			// com.android.internal.R.attr.textSelectHandleWindowStyle);
			// mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
			// mPopupWindow.setClippingEnabled(true);
		}

		@Override
		protected void initContentView() {
			// LinearLayout linearLayout = new LinearLayout(mTextView.getContext());
			// linearLayout.setOrientation(LinearLayout.HORIZONTAL);
			// mContentView = linearLayout;
			// mContentView.setBackgroundResource(com.android.internal.R.drawable.text_edit_side_paste_window);
			//
			// LayoutInflater inflater = (LayoutInflater) mTextView.getContext().getSystemService(
			// Context.LAYOUT_INFLATER_SERVICE);
			//
			// LayoutParams wrapContent = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
			// ViewGroup.LayoutParams.WRAP_CONTENT);
			//
			// mDeleteTextView = (TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
			// mDeleteTextView.setLayoutParams(wrapContent);
			// mDeleteTextView.setText(com.android.internal.R.string.delete);
			// mDeleteTextView.setOnClickListener(this);
			// mContentView.addView(mDeleteTextView);
		}

		public void setEasyEditSpan(EasyEditSpan easyEditSpan) {
			mEasyEditSpan = easyEditSpan;
		}

		@Override
		public void onClick(View view) {
			if (view == mDeleteTextView) {
				Editable editable = (Editable) mTextView.getText();
				int start = editable.getSpanStart(mEasyEditSpan);
				int end = editable.getSpanEnd(mEasyEditSpan);
				if (start >= 0 && end >= 0) {
					mTextView.deleteText_internal(start, end);
				}
			}
		}

		@Override
		protected int getTextOffset() {
			// Place the pop-up at the end of the span
			Editable editable = (Editable) mTextView.getText();
			return editable.getSpanEnd(mEasyEditSpan);
		}

		@Override
		protected int getVerticalLocalPosition(int line) {
			return mTextView.getLayout().getLineBottom(line);
		}

		@Override
		protected int clipVertically(int positionY) {
			// As we display the pop-up below the span, no vertical clipping is required.
			return positionY;
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

	private abstract class PinnedPopupWindow implements TextViewPositionListener {
		protected PopupWindow mPopupWindow;
		protected ViewGroup mContentView;
		int mPositionX, mPositionY;

		protected abstract void createPopupWindow();

		protected abstract void initContentView();

		protected abstract int getTextOffset();

		protected abstract int getVerticalLocalPosition(int line);

		protected abstract int clipVertically(int positionY);

		public PinnedPopupWindow() {
			createPopupWindow();

			// mPopupWindow.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
			mPopupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
			mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

			initContentView();

			LayoutParams wrapContent = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			mContentView.setLayoutParams(wrapContent);

			mPopupWindow.setContentView(mContentView);
		}

		public void show() {
			getPositionListener().addSubscriber(this, false /* offset is fixed */);

			computeLocalPosition();

			final PositionListener positionListener = getPositionListener();
			updatePosition(positionListener.getPositionX(), positionListener.getPositionY());
		}

		protected void measureContent() {
			final DisplayMetrics displayMetrics = mTextView.getResources().getDisplayMetrics();
			mContentView.measure(
					View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
					View.MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels, View.MeasureSpec.AT_MOST));
		}

		/*
		 * The popup window will be horizontally centered on the getTextOffset() and vertically positioned according to
		 * viewportToContentHorizontalOffset.
		 * 
		 * This method assumes that mContentView has properly been measured from its content.
		 */
		private void computeLocalPosition() {
			measureContent();
			final int width = mContentView.getMeasuredWidth();
			final int offset = getTextOffset();
			mPositionX = (int) (mTextView.getLayout().getPrimaryHorizontal(offset) - width / 2.0f);
			mPositionX += mTextView.viewportToContentHorizontalOffset();

			final int line = mTextView.getLayout().getLineForOffset(offset);
			mPositionY = getVerticalLocalPosition(line);
			mPositionY += mTextView.viewportToContentVerticalOffset();
		}

		private void updatePosition(int parentPositionX, int parentPositionY) {
			int positionX = parentPositionX + mPositionX;
			int positionY = parentPositionY + mPositionY;

			positionY = clipVertically(positionY);

			// Horizontal clipping
			final DisplayMetrics displayMetrics = mTextView.getResources().getDisplayMetrics();
			final int width = mContentView.getMeasuredWidth();
			positionX = Math.min(displayMetrics.widthPixels - width, positionX);
			positionX = Math.max(0, positionX);

			if (isShowing()) {
				mPopupWindow.update(positionX, positionY, -1, -1);
			} else {
				mPopupWindow.showAtLocation(mTextView, Gravity.NO_GRAVITY, positionX, positionY);
			}
		}

		public void hide() {
			mPopupWindow.dismiss();
			getPositionListener().removeSubscriber(this);
		}

		@Override
		public void updatePosition(int parentPositionX, int parentPositionY, boolean parentPositionChanged,
				boolean parentScrolled) {
			// Either parentPositionChanged or parentScrolled is true, check if still visible
			if (isShowing() && isOffsetVisible(getTextOffset())) {
				if (parentScrolled)
					computeLocalPosition();
				updatePosition(parentPositionX, parentPositionY);
			} else {
				hide();
			}
		}

		public boolean isShowing() {
			return mPopupWindow.isShowing();
		}
	}

	/**
	 * An ActionMode Callback class that is used to provide actions while in text selection mode.
	 * 
	 * The default callback provides a subset of Select All, Cut, Copy and Paste actions, depending on which of these
	 * this TextView supports.
	 */
	private class SelectionActionModeCallback implements ActionMode.Callback {

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// TypedArray styledAttributes = mTextView.getContext().obtainStyledAttributes(
			// com.android.internal.R.styleable.SelectionModeDrawables);
			//
			// mode.setTitle(mTextView.getContext().getString(
			// com.android.internal.R.string.textSelectionCABTitle));
			// mode.setSubtitle(null);
			// mode.setTitleOptionalHint(true);
			//
			// menu.add(0, TextView.ID_SELECT_ALL, 0, com.android.internal.R.string.selectAll)
			// .setIcon(
			// styledAttributes.getResourceId(
			// R.styleable.SelectionModeDrawables_actionModeSelectAllDrawable, 0))
			// .setAlphabeticShortcut('a')
			// .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			//
			// if (mTextView.canCut()) {
			// menu.add(0, TextView.ID_CUT, 0, com.android.internal.R.string.cut)
			// .setIcon(
			// styledAttributes.getResourceId(
			// R.styleable.SelectionModeDrawables_actionModeCutDrawable, 0))
			// .setAlphabeticShortcut('x')
			// .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			// }
			//
			// if (mTextView.canCopy()) {
			// menu.add(0, TextView.ID_COPY, 0, com.android.internal.R.string.copy)
			// .setIcon(
			// styledAttributes.getResourceId(
			// R.styleable.SelectionModeDrawables_actionModeCopyDrawable, 0))
			// .setAlphabeticShortcut('c')
			// .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			// }
			//
			// if (mTextView.canPaste()) {
			// menu.add(0, TextView.ID_PASTE, 0, com.android.internal.R.string.paste)
			// .setIcon(
			// styledAttributes.getResourceId(
			// R.styleable.SelectionModeDrawables_actionModePasteDrawable, 0))
			// .setAlphabeticShortcut('v')
			// .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			// }
			//
			// styledAttributes.recycle();

			if (mCustomSelectionActionModeCallback != null) {
				if (!mCustomSelectionActionModeCallback.onCreateActionMode(mode, menu)) {
					// The custom mode can choose to cancel the action mode
					return false;
				}
			}

			if (menu.hasVisibleItems() || mode.getCustomView() != null) {
				getSelectionController().show();
				mTextView.setHasTransientState(true);
				return true;
			} else {
				return false;
			}
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			if (mCustomSelectionActionModeCallback != null) {
				return mCustomSelectionActionModeCallback.onPrepareActionMode(mode, menu);
			}
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			if (mCustomSelectionActionModeCallback != null
					&& mCustomSelectionActionModeCallback.onActionItemClicked(mode, item)) {
				return true;
			}
			return mTextView.onTextContextMenuItem(item.getItemId());
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			if (mCustomSelectionActionModeCallback != null) {
				mCustomSelectionActionModeCallback.onDestroyActionMode(mode);
			}

			/*
			 * If we're ending this mode because we're detaching from a window, we still have selection state to
			 * preserve. Don't clear it, we'll bring back the selection mode when (if) we get reattached.
			 */
			if (!mPreserveDetachedSelection) {
				Selection.setSelection((Spannable) mTextView.getText(), mTextView.getSelectionEnd());
				mTextView.setHasTransientState(false);
			}

			if (mSelectionModifierCursorController != null) {
				mSelectionModifierCursorController.hide();
			}

			mSelectionActionMode = null;
		}
	}

	private class ActionPopupWindow extends PinnedPopupWindow implements OnClickListener {
		// private static final int POPUP_TEXT_LAYOUT = com.android.internal.R.layout.text_edit_action_popup_text;
		private TextView mPasteTextView;
		private TextView mReplaceTextView;

		@Override
		protected void createPopupWindow() {
			// mPopupWindow = new PopupWindow(mTextView.getContext(), null,
			// com.android.internal.R.attr.textSelectHandleWindowStyle);
			// mPopupWindow.setClippingEnabled(true);
		}

		@Override
		protected void initContentView() {
			// LinearLayout linearLayout = new LinearLayout(mTextView.getContext());
			// linearLayout.setOrientation(LinearLayout.HORIZONTAL);
			// mContentView = linearLayout;
			// mContentView.setBackgroundResource(com.android.internal.R.drawable.text_edit_paste_window);
			//
			// LayoutInflater inflater = (LayoutInflater) mTextView.getContext().getSystemService(
			// Context.LAYOUT_INFLATER_SERVICE);
			//
			// LayoutParams wrapContent = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
			// ViewGroup.LayoutParams.WRAP_CONTENT);
			//
			// mPasteTextView = (TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
			// mPasteTextView.setLayoutParams(wrapContent);
			// mContentView.addView(mPasteTextView);
			// mPasteTextView.setText(com.android.internal.R.string.paste);
			// mPasteTextView.setOnClickListener(this);
			//
			// mReplaceTextView = (TextView) inflater.inflate(POPUP_TEXT_LAYOUT, null);
			// mReplaceTextView.setLayoutParams(wrapContent);
			// mContentView.addView(mReplaceTextView);
			// mReplaceTextView.setText(com.android.internal.R.string.replace);
			// mReplaceTextView.setOnClickListener(this);
		}

		@Override
		public void show() {
			boolean canPaste = mTextView.canPaste();
			boolean canSuggest = mTextView.isSuggestionsEnabled() && isCursorInsideSuggestionSpan();
			mPasteTextView.setVisibility(canPaste ? View.VISIBLE : View.GONE);
			mReplaceTextView.setVisibility(canSuggest ? View.VISIBLE : View.GONE);

			if (!canPaste && !canSuggest)
				return;

			super.show();
		}

		@Override
		public void onClick(View view) {
			if (view == mPasteTextView && mTextView.canPaste()) {
				mTextView.onTextContextMenuItem(TextView.ID_PASTE);
				hide();
			} else if (view == mReplaceTextView) {
				int middle = (mTextView.getSelectionStart() + mTextView.getSelectionEnd()) / 2;
				stopSelectionActionMode();
				Selection.setSelection((Spannable) mTextView.getText(), middle);
				// showSuggestions();
			}
		}

		@Override
		protected int getTextOffset() {
			return (mTextView.getSelectionStart() + mTextView.getSelectionEnd()) / 2;
		}

		@Override
		protected int getVerticalLocalPosition(int line) {
			return mTextView.getLayout().getLineTop(line) - mContentView.getMeasuredHeight();
		}

		@Override
		protected int clipVertically(int positionY) {
			if (positionY < 0) {
				final int offset = getTextOffset();
				final Layout layout = mTextView.getLayout();
				final int line = layout.getLineForOffset(offset);
				positionY += layout.getLineBottom(line) - layout.getLineTop(line);
				positionY += mContentView.getMeasuredHeight();

				// Assumes insertion and selection handles share the same height
				final Drawable handle = mTextView.getResources().getDrawable(mTextView.mTextSelectHandleRes);
				positionY += handle.getIntrinsicHeight();
			}

			return positionY;
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
		protected ActionPopupWindow mActionPopupWindow;
		// Previous text character offset
		private int mPreviousOffset = -1;
		// Previous text character offset
		private boolean mPositionHasChanged = true;
		// Used to delay the appearance of the action popup window
		private Runnable mActionPopupShower;

		public HandleView(Drawable drawableLtr, Drawable drawableRtl) {
			super(mTextView.getContext());
			mContainer = null;
			// mContainer = new PopupWindow(mTextView.getContext(), null,
			// com.android.internal.R.attr.textSelectHandleWindowStyle);
			// mContainer.setSplitTouchEnabled(true);
			// mContainer.setClippingEnabled(false);
			// mContainer.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
			// mContainer.setContentView(this);
			//
			// mDrawableLtr = drawableLtr;
			// mDrawableRtl = drawableRtl;
			//
			// updateDrawable();
			//
			// final int handleHeight = mDrawable.getIntrinsicHeight();
			// mTouchOffsetY = -0.3f * handleHeight;
			// mIdealVerticalOffset = 0.7f * handleHeight;
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

			hideActionPopupWindow();
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

		void showActionPopupWindow(int delay) {
			if (mActionPopupWindow == null) {
				mActionPopupWindow = new ActionPopupWindow();
			}
			if (mActionPopupShower == null) {
				mActionPopupShower = new Runnable() {
					public void run() {
						mActionPopupWindow.show();
					}
				};
			} else {
				mTextView.removeCallbacks(mActionPopupShower);
			}
			mTextView.postDelayed(mActionPopupShower, delay);
		}

		protected void hideActionPopupWindow() {
			if (mActionPopupShower != null) {
				mTextView.removeCallbacks(mActionPopupShower);
			}
			if (mActionPopupWindow != null) {
				mActionPopupWindow.hide();
			}
		}

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

		public boolean isDragging() {
			return mIsDragging;
		}

		void onHandleMoved() {
			hideActionPopupWindow();
		}

		public void onDetached() {
			hideActionPopupWindow();
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
				showActionPopupWindow(0);
			}

			hideAfterDelay();
		}

		public void showWithActionPopup() {
			show();
			showActionPopupWindow(0);
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
						if (mActionPopupWindow != null && mActionPopupWindow.isShowing()) {
							// Tapping on the handle dismisses the displayed action popup
							mActionPopupWindow.hide();
						} else {
							showWithActionPopup();
						}
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

	private class SelectionStartHandleView extends HandleView {

		public SelectionStartHandleView(Drawable drawableLtr, Drawable drawableRtl) {
			super(drawableLtr, drawableRtl);
		}

		@Override
		protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
			if (isRtlRun) {
				return drawable.getIntrinsicWidth() / 4;
			} else {
				return (drawable.getIntrinsicWidth() * 3) / 4;
			}
		}

		@Override
		public int getCurrentCursorOffset() {
			return mTextView.getSelectionStart();
		}

		@Override
		public void updateSelection(int offset) {
			Selection.setSelection((Spannable) mTextView.getText(), offset, mTextView.getSelectionEnd());
			updateDrawable();
		}

		@Override
		public void updatePosition(float x, float y) {
			int offset = mTextView.getOffsetForPosition(x, y);

			// Handles can not cross and selection is at least one character
			final int selectionEnd = mTextView.getSelectionEnd();
			if (offset >= selectionEnd)
				offset = Math.max(0, selectionEnd - 1);

			positionAtCursorOffset(offset, false);
		}

		public ActionPopupWindow getActionPopupWindow() {
			return mActionPopupWindow;
		}
	}

	private class SelectionEndHandleView extends HandleView {

		public SelectionEndHandleView(Drawable drawableLtr, Drawable drawableRtl) {
			super(drawableLtr, drawableRtl);
		}

		@Override
		protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
			if (isRtlRun) {
				return (drawable.getIntrinsicWidth() * 3) / 4;
			} else {
				return drawable.getIntrinsicWidth() / 4;
			}
		}

		@Override
		public int getCurrentCursorOffset() {
			return mTextView.getSelectionEnd();
		}

		@Override
		public void updateSelection(int offset) {
			Selection.setSelection((Spannable) mTextView.getText(), mTextView.getSelectionStart(), offset);
			updateDrawable();
		}

		@Override
		public void updatePosition(float x, float y) {
			int offset = mTextView.getOffsetForPosition(x, y);

			// Handles can not cross and selection is at least one character
			final int selectionStart = mTextView.getSelectionStart();
			if (offset <= selectionStart) {
				offset = Math.min(selectionStart + 1, mTextView.getText().length());
			}

			positionAtCursorOffset(offset, false);
		}

		public void setActionPopupWindow(ActionPopupWindow actionPopupWindow) {
			mActionPopupWindow = actionPopupWindow;
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

	class SelectionModifierCursorController implements CursorController {
		private static final int DELAY_BEFORE_REPLACE_ACTION = 200; // milliseconds
		// The cursor controller handles, lazily created when shown.
		private SelectionStartHandleView mStartHandle;
		private SelectionEndHandleView mEndHandle;
		// The offsets of that last touch down event. Remembered to start selection there.
		private int mMinTouchOffset, mMaxTouchOffset;

		// Double tap detection
		private long mPreviousTapUpTime = 0;
		private float mDownPositionX, mDownPositionY;
		private boolean mGestureStayedInTapRegion;

		SelectionModifierCursorController() {
			resetTouchOffsets();
		}

		public void show() {
			if (mTextView.isInBatchEditMode()) {
				return;
			}
			initDrawables();
			initHandles();
			hideInsertionPointCursorController();
		}

		private void initDrawables() {
			if (mSelectHandleLeft == null) {
				mSelectHandleLeft = mTextView.getContext().getResources()
						.getDrawable(mTextView.mTextSelectHandleLeftRes);
			}
			if (mSelectHandleRight == null) {
				mSelectHandleRight = mTextView.getContext().getResources()
						.getDrawable(mTextView.mTextSelectHandleRightRes);
			}
		}

		private void initHandles() {
			// Lazy object creation has to be done before updatePosition() is called.
			if (mStartHandle == null) {
				mStartHandle = new SelectionStartHandleView(mSelectHandleLeft, mSelectHandleRight);
			}
			if (mEndHandle == null) {
				mEndHandle = new SelectionEndHandleView(mSelectHandleRight, mSelectHandleLeft);
			}

			mStartHandle.show();
			mEndHandle.show();

			// Make sure both left and right handles share the same ActionPopupWindow (so that
			// moving any of the handles hides the action popup).
			mStartHandle.showActionPopupWindow(DELAY_BEFORE_REPLACE_ACTION);
			mEndHandle.setActionPopupWindow(mStartHandle.getActionPopupWindow());

			hideInsertionPointCursorController();
		}

		public void hide() {
			if (mStartHandle != null)
				mStartHandle.hide();
			if (mEndHandle != null)
				mEndHandle.hide();
		}

		public void onTouchEvent(MotionEvent event) {
			// This is done even when the View does not have focus, so that long presses can start
			// selection and tap can move cursor from this tap position.
			switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				final float x = event.getX();
				final float y = event.getY();

				// Remember finger down position, to be able to start selection from there
				mMinTouchOffset = mMaxTouchOffset = mTextView.getOffsetForPosition(x, y);

				// Double tap detection
				if (mGestureStayedInTapRegion) {
					long duration = SystemClock.uptimeMillis() - mPreviousTapUpTime;
					if (duration <= ViewConfiguration.getDoubleTapTimeout()) {
						final float deltaX = x - mDownPositionX;
						final float deltaY = y - mDownPositionY;
						final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

						ViewConfiguration viewConfiguration = ViewConfiguration.get(mTextView.getContext());
						int doubleTapSlop = viewConfiguration.getScaledDoubleTapSlop();
						boolean stayedInArea = distanceSquared < doubleTapSlop * doubleTapSlop;

						if (stayedInArea && isPositionOnText(x, y)) {
							startSelectionActionMode();
							mDiscardNextActionUp = true;
						}
					}
				}

				mDownPositionX = x;
				mDownPositionY = y;
				mGestureStayedInTapRegion = true;
				break;

			case MotionEvent.ACTION_POINTER_DOWN:
			case MotionEvent.ACTION_POINTER_UP:
				// Handle multi-point gestures. Keep min and max offset positions.
				// Only activated for devices that correctly handle multi-touch.
				if (mTextView.getContext().getPackageManager()
						.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) {
					updateMinAndMaxOffsets(event);
				}
				break;

			case MotionEvent.ACTION_MOVE:
				if (mGestureStayedInTapRegion) {
					// final float deltaX = event.getX() - mDownPositionX;
					// final float deltaY = event.getY() - mDownPositionY;
					// final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

					// final ViewConfiguration viewConfiguration = ViewConfiguration.get(mTextView.getContext());
					// int doubleTapTouchSlop = viewConfiguration.getScaledDoubleTapTouchSlop();

					// if (distanceSquared > doubleTapTouchSlop * doubleTapTouchSlop) {
					// mGestureStayedInTapRegion = false;
					// }
				}
				break;

			case MotionEvent.ACTION_UP:
				mPreviousTapUpTime = SystemClock.uptimeMillis();
				break;
			}
		}

		/**
		 * @param event
		 */
		private void updateMinAndMaxOffsets(MotionEvent event) {
			int pointerCount = event.getPointerCount();
			for (int index = 0; index < pointerCount; index++) {
				int offset = mTextView.getOffsetForPosition(event.getX(index), event.getY(index));
				if (offset < mMinTouchOffset)
					mMinTouchOffset = offset;
				if (offset > mMaxTouchOffset)
					mMaxTouchOffset = offset;
			}
		}

		public int getMinTouchOffset() {
			return mMinTouchOffset;
		}

		public int getMaxTouchOffset() {
			return mMaxTouchOffset;
		}

		public void resetTouchOffsets() {
			mMinTouchOffset = mMaxTouchOffset = -1;
		}

		/**
		 * @return true iff this controller is currently used to move the selection start.
		 */
		public boolean isSelectionStartDragged() {
			return mStartHandle != null && mStartHandle.isDragging();
		}

		public void onTouchModeChanged(boolean isInTouchMode) {
			if (!isInTouchMode) {
				hide();
			}
		}

		@Override
		public void onDetached() {
			final ViewTreeObserver observer = mTextView.getViewTreeObserver();
			observer.removeOnTouchModeChangeListener(this);

			if (mStartHandle != null)
				mStartHandle.onDetached();
			if (mEndHandle != null)
				mEndHandle.onDetached();
		}
	}

	private class CorrectionHighlighter {
		private final Path mPath = new Path();
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private int mStart, mEnd;
		private long mFadingStartTime;
		private RectF mTempRectF;
		private final static int FADE_OUT_DURATION = 400;

		public CorrectionHighlighter() {
			// mPaint.setCompatibilityScaling(mTextView.getResources().getCompatibilityInfo().applicationScale);
			mPaint.setStyle(Paint.Style.FILL);
		}

		public void highlight(CorrectionInfo info) {
			mStart = info.getOffset();
			mEnd = mStart + info.getNewText().length();
			mFadingStartTime = SystemClock.uptimeMillis();

			if (mStart < 0 || mEnd < 0) {
				stopAnimation();
			}
		}

		public void draw(Canvas canvas, int cursorOffsetVertical) {
			if (updatePath() && updatePaint()) {
				if (cursorOffsetVertical != 0) {
					canvas.translate(0, cursorOffsetVertical);
				}

				canvas.drawPath(mPath, mPaint);

				if (cursorOffsetVertical != 0) {
					canvas.translate(0, -cursorOffsetVertical);
				}
				invalidate(true); // TODO invalidate cursor region only
			} else {
				stopAnimation();
				invalidate(false); // TODO invalidate cursor region only
			}
		}

		private boolean updatePaint() {
			final long duration = SystemClock.uptimeMillis() - mFadingStartTime;
			if (duration > FADE_OUT_DURATION)
				return false;

			final float coef = 1.0f - (float) duration / FADE_OUT_DURATION;
			final int highlightColorAlpha = Color.alpha(mTextView.mHighlightColor);
			final int color = (mTextView.mHighlightColor & 0x00FFFFFF) + ((int) (highlightColorAlpha * coef) << 24);
			mPaint.setColor(color);
			return true;
		}

		private boolean updatePath() {
			final Layout layout = mTextView.getLayout();
			if (layout == null)
				return false;

			// Update in case text is edited while the animation is run
			final int length = mTextView.getText().length();
			int start = Math.min(length, mStart);
			int end = Math.min(length, mEnd);

			mPath.reset();
			layout.getSelectionPath(start, end, mPath);
			return true;
		}

		private void invalidate(boolean delayed) {
			if (mTextView.getLayout() == null)
				return;

			if (mTempRectF == null)
				mTempRectF = new RectF();
			mPath.computeBounds(mTempRectF, false);

			int left = mTextView.getCompoundPaddingLeft();
			int top = mTextView.getExtendedPaddingTop() + mTextView.getVerticalOffset(true);

			if (delayed) {
				mTextView.postInvalidateOnAnimation(left + (int) mTempRectF.left, top + (int) mTempRectF.top, left
						+ (int) mTempRectF.right, top + (int) mTempRectF.bottom);
			} else {
				mTextView.postInvalidate((int) mTempRectF.left, (int) mTempRectF.top, (int) mTempRectF.right,
						(int) mTempRectF.bottom);
			}
		}

		private void stopAnimation() {
			Editor.this.mCorrectionHighlighter = null;
		}
	}

	@SuppressWarnings("unused")
	private static class ErrorPopup extends PopupWindow {
		private boolean mAbove = false;
		private final TextView mView;
		private int mPopupInlineErrorBackgroundId = 0;
		private int mPopupInlineErrorAboveBackgroundId = 0;

		ErrorPopup(TextView v, int width, int height) {
			super(v, width, height);
			mView = v;
			// Make sure the TextView has a background set as it will be used the first time it is
			// shown and positioned. Initialized with below background, which should have
			// dimensions identical to the above version for this to work (and is more likely).
			// mPopupInlineErrorBackgroundId = getResourceId(mPopupInlineErrorBackgroundId,
			// com.android.internal.R.styleable.Theme_errorMessageBackground);
			// mView.setBackgroundResource(mPopupInlineErrorBackgroundId);
		}

		void fixDirection(boolean above) {
			mAbove = above;

			// if (above) {
			// mPopupInlineErrorAboveBackgroundId = getResourceId(mPopupInlineErrorAboveBackgroundId,
			// com.android.internal.R.styleable.Theme_errorMessageAboveBackground);
			// } else {
			// mPopupInlineErrorBackgroundId = getResourceId(mPopupInlineErrorBackgroundId,
			// com.android.internal.R.styleable.Theme_errorMessageBackground);
			// }

			mView.setBackgroundResource(above ? mPopupInlineErrorAboveBackgroundId : mPopupInlineErrorBackgroundId);
		}

		// private int getResourceId(int currentId, int index) {
		// // if (currentId == 0) {
		// // TypedArray styledAttributes = mView.getContext().obtainStyledAttributes(R.styleable.Theme);
		// // currentId = styledAttributes.getResourceId(index, 0);
		// // styledAttributes.recycle();
		// // }
		// return currentId;
		// }

		@Override
		public void update(int x, int y, int w, int h, boolean force) {
			super.update(x, y, w, h, force);

			boolean above = isAboveAnchor();
			if (above != mAbove) {
				fixDirection(above);
			}
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

	// /**
	// * @hide
	// */
	// public static class UserDictionaryListener extends Handler {
	// public TextView mTextView;
	// public String mOriginalWord;
	// public int mWordStart;
	// public int mWordEnd;
	//
	// public void waitForUserDictionaryAdded(TextView tv, String originalWord, int spanStart, int spanEnd) {
	// mTextView = tv;
	// mOriginalWord = originalWord;
	// mWordStart = spanStart;
	// mWordEnd = spanEnd;
	// }
	//
	// @Override
	// public void handleMessage(Message msg) {
	// switch (msg.what) {
	// case 0: /* CODE_WORD_ADDED */
	// case 2: /* CODE_ALREADY_PRESENT */
	// if (!(msg.obj instanceof Bundle)) {
	// Log.w(TAG, "Illegal message. Abort handling onUserDictionaryAdded.");
	// return;
	// }
	// final Bundle bundle = (Bundle) msg.obj;
	// final String originalWord = bundle.getString("originalWord");
	// final String addedWord = bundle.getString("word");
	// onUserDictionaryAdded(originalWord, addedWord);
	// return;
	// default:
	// return;
	// }
	// }
	//
	// private void onUserDictionaryAdded(String originalWord, String addedWord) {
	// if (TextUtils.isEmpty(mOriginalWord) || TextUtils.isEmpty(addedWord)) {
	// return;
	// }
	// if (mWordStart < 0 || mWordEnd >= mTextView.length()) {
	// return;
	// }
	// if (!mOriginalWord.equals(originalWord)) {
	// return;
	// }
	// if (originalWord.equals(addedWord)) {
	// return;
	// }
	// final Editable editable = (Editable) mTextView.getText();
	// final String currentWord = editable.toString().substring(mWordStart, mWordEnd);
	// if (!currentWord.equals(originalWord)) {
	// return;
	// }
	// mTextView.replaceText_internal(mWordStart, mWordEnd, addedWord);
	// // Move cursor at the end of the replaced word
	// final int newCursorPosition = mWordStart + addedWord.length();
	// mTextView.setCursorPosition_internal(newCursorPosition, newCursorPosition);
	// }
	// }

}
