package lah.texpert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import org.xmlpull.v1.XmlPullParserException;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.DateKeyListener;
import android.text.method.DateTimeKeyListener;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.MetaKeyKeyListener;
import android.text.method.MovementMethod;
import android.text.method.TextKeyListener;
import android.text.method.TimeKeyListener;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;
import android.text.style.UpdateAppearance;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.RemoteViews.RemoteView;
import android.widget.Scroller;

@SuppressLint({ "WrongCall", "FloatMath" })
@RemoteView
public class TextView extends View implements ViewTreeObserver.OnPreDrawListener {

	public interface OnEditorActionListener {

		boolean onEditorAction(TextView textView, int actionCode, Object object);

	}

	static final String LOG_TAG = "TextView";
	static final boolean DEBUG_EXTRACT = false;

	private static final int LINES = 1;
	private static final int EMS = LINES;
	private static final int PIXELS = 2;

	private static final RectF TEMP_RECTF = new RectF();

	// XXX should be much larger
	private static final int VERY_WIDE = 1024 * 1024;
	private static final int ANIMATED_SCROLL_GAP = 250;

	private static final int CHANGE_WATCHER_PRIORITY = 100;

	// System wide time for last cut or copy action.
	static long LAST_CUT_OR_COPY_TIME;

	private ColorStateList mTextColor;
	private ColorStateList mLinkTextColor;
	private int mCurTextColor;
	private boolean mFreezesText;
	private boolean mTemporaryDetach;
	private boolean mDispatchTemporaryDetach;

	private Editable.Factory mEditableFactory = Editable.Factory.getInstance();

	private float mShadowRadius, mShadowDx, mShadowDy;

	private boolean mPreDrawRegistered;

	// The alignment to pass to Layout, or null if not resolved.
	private Layout.Alignment mLayoutAlignment;
	private int mResolvedTextAlignment;

	@ViewDebug.ExportedProperty(category = "text")
	private CharSequence mText;
	private CharSequence mTransformed;

	private MovementMethod mMovement;

	private ChangeWatcher mChangeWatcher;

	private ArrayList<TextWatcher> mListeners;

	// display attributes
	private final TextPaint mTextPaint;
	private boolean mUserSetTextScaleX;
	private Layout mLayout;

	private int mGravity = Gravity.TOP | Gravity.START;
	private boolean mHorizontallyScrolling;

	/**
	 * Fast round from float to int. This is faster than Math.round() thought it may return slightly different results.
	 * It does not try to handle (in any meaningful way) NaN or infinities.
	 */
	public static int round(float value) {
		long lx = (long) (value * (65536 * 256f));
		return (int) ((lx + 0x800000) >> 24);
	}

	private float mSpacingMult = 1.0f;
	private float mSpacingAdd = 0.0f;

	private int mMaximum = Integer.MAX_VALUE;
	private int mMaxMode = LINES;
	private int mMinimum = 0;
	private int mMinMode = LINES;

	private int mOldMaximum = mMaximum;
	private int mOldMaxMode = mMaxMode;

	private int mMaxWidth = Integer.MAX_VALUE;
	private int mMaxWidthMode = PIXELS;
	private int mMinWidth = 0;
	private int mMinWidthMode = PIXELS;

	private boolean mSingleLine;
	private int mDesiredHeightAtMeasure = -1;
	private boolean mIncludePad = true;
	private int mDeferScroll = -1;

	// tmp primitives, so we don't alloc them on each draw
	private Rect mTempRect;
	private long mLastScroll;
	private Scroller mScroller;

	private BoringLayout.Metrics mBoring;
	private BoringLayout mSavedLayout, mSavedHintLayout;

	// It is possible to have a selection even when mEditor is null (programmatically set, like when
	// a link is pressed). These highlight-related fields do not go in mEditor.
	int mHighlightColor = 0x6633B5E5;
	private Path mHighlightPath;
	private final Paint mHighlightPaint;
	private boolean mHighlightPathBogus = true;

	int mCursorDrawableRes;
	// int mTextSelectHandleLeftRes = R.drawable.text_select_handle_left;
	// int mTextSelectHandleRightRes = R.drawable.text_select_handle_right;
	int mTextSelectHandleRes = R.drawable.text_select_handle_middle;

	private Editor mEditor = new Editor(this);

	/*
	 * Kick-start the font cache for the zygote process (to pay the cost of initializing freetype for our default font
	 * only once).
	 */
	static {
		Paint p = new Paint();
		p.setAntiAlias(true);
		// We don't care about the result, just the side-effect of measuring.
		p.measureText("H");
	}

	public TextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mText = "";

		final Resources res = getResources();

		mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
		mTextPaint.density = res.getDisplayMetrics().density;
		mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		ColorStateList textColor = null;
		int textSize = 20;
		boolean editable = true;
		CharSequence text = "";

		if (isTextSelectable()) {
			// Prevent text changes from keyboard.
			if (true) {
				mEditor.mKeyListener = null;
				mEditor.mInputType = EditorInfo.TYPE_NULL;
			}
			// So that selection can be changed using arrow keys and touch is handled.
			setMovementMethod(ArrowKeyMovementMethod.getInstance());
		} else if (editable) {
			mEditor.mKeyListener = TextKeyListener.getInstance();
			mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
		} else {
			mEditor.mKeyListener = null;
		}
		setMovementMethod(ArrowKeyMovementMethod.getInstance());
		setTextColor(textColor != null ? textColor : ColorStateList.valueOf(0xFF000000));
		setRawTextSize(textSize);
		setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
		setText(text);
		boolean focusable = mMovement != null || getKeyListener() != null;
		boolean clickable = focusable;
		boolean longClickable = focusable;
		setFocusable(focusable);
		setClickable(clickable);
		setLongClickable(longClickable);
		mEditor.prepareCursorControllers();
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (enabled == isEnabled()) {
			return;
		}

		if (!enabled) {
			// Hide the soft input if the currently active TextView is disabled
			InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null && imm.isActive(this)) {
				imm.hideSoftInputFromWindow(getWindowToken(), 0);
			}
		}

		super.setEnabled(enabled);

		if (enabled) {
			// Make sure IME is updated with current editor info.
			InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null)
				imm.restartInput(this);
		}

		mEditor.prepareCursorControllers();
		mEditor.makeBlink();
	}

	public void setTypeface(Typeface tf, int style) {
		if (style > 0) {
			if (tf == null) {
				tf = Typeface.defaultFromStyle(style);
			} else {
				tf = Typeface.create(tf, style);
			}

			setTypeface(tf);
			// now compute what (if any) algorithmic styling is needed
			int typefaceStyle = tf != null ? tf.getStyle() : 0;
			int need = style & ~typefaceStyle;
			mTextPaint.setFakeBoldText((need & Typeface.BOLD) != 0);
			mTextPaint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
		} else {
			mTextPaint.setFakeBoldText(false);
			mTextPaint.setTextSkewX(0);
			setTypeface(tf);
		}
	}

	@ViewDebug.CapturedViewProperty
	public CharSequence getText() {
		return mText;
	}

	/**
	 * Returns the length, in characters, of the text managed by this TextView
	 */
	public int length() {
		return mText.length();
	}

	public Editable getEditableText() {
		return (mText instanceof Editable) ? (Editable) mText : null;
	}

	/**
	 * @return the height of one standard line in pixels. Note that markup within the text can cause individual lines to
	 *         be taller or shorter than this height, and the layout may contain additional first- or last-line padding.
	 */
	public int getLineHeight() {
		return round(mTextPaint.getFontMetricsInt(null) * mSpacingMult + mSpacingAdd);
	}

	/**
	 * @return the Layout that is currently being used to display the text. This can be null if the text or width has
	 *         recently changes.
	 */
	public final Layout getLayout() {
		return mLayout;
	}

	/**
	 * @return the current key listener for this TextView. This will frequently be null for non-EditText TextViews.
	 */
	public final KeyListener getKeyListener() {
		return mEditor.mKeyListener;
	}

	/**
	 * Sets the key listener to be used with this TextView. This can be null to disallow user input. Note that this
	 * method has significant and subtle interactions with soft keyboards and other input method: see
	 * {@link KeyListener#getInputType() KeyListener.getContentType()} for important details. Calling this method will
	 * replace the current content type of the text view with the content type returned by the key listener.
	 * <p>
	 * Be warned that if you want a TextView with a key listener or movement method not to be focusable, or if you want
	 * a TextView without a key listener or movement method to be focusable, you must call {@link #setFocusable} again
	 * after calling this to get the focusability back the way you want it.
	 */
	public void setKeyListener(KeyListener input) {
		setKeyListenerOnly(input);
		fixFocusableAndClickableSettings();

		if (input != null) {
			try {
				mEditor.mInputType = mEditor.mKeyListener.getInputType();
			} catch (IncompatibleClassChangeError e) {
				mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
			}
			// Change inputType, without affecting transformation.
			// No need to applySingleLine since mSingleLine is unchanged.
			// setInputTypeSingleLine(mSingleLine);
			mEditor.mInputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
		} else {
			mEditor.mInputType = EditorInfo.TYPE_NULL;
		}

		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.restartInput(this);
	}

	private void setKeyListenerOnly(KeyListener input) {
		if (mEditor.mKeyListener != input) {
			mEditor.mKeyListener = input;
			if (input != null && !(mText instanceof Editable)) {
				setText(mText);
			}
		}
	}

	/**
	 * @return the movement method being used for this TextView. This will frequently be null for non-EditText
	 *         TextViews.
	 */
	public final MovementMethod getMovementMethod() {
		return mMovement;
	}

	/**
	 * Sets the movement method (arrow key handler) to be used for this TextView. This can be null to disallow using the
	 * arrow keys to move the cursor or scroll the view.
	 * <p>
	 * Be warned that if you want a TextView with a key listener or movement method not to be focusable, or if you want
	 * a TextView without a key listener or movement method to be focusable, you must call {@link #setFocusable} again
	 * after calling this to get the focusability back the way you want it.
	 */
	public final void setMovementMethod(MovementMethod movement) {
		if (mMovement != movement) {
			mMovement = movement;

			if (movement != null && !(mText instanceof Spannable)) {
				setText(mText);
			}

			fixFocusableAndClickableSettings();

			// SelectionModifierCursorController depends on textCanBeSelected, which depends on
			// mMovement
			mEditor.prepareCursorControllers();
		}
	}

	private void fixFocusableAndClickableSettings() {
		if (mMovement != null || (mEditor.mKeyListener != null)) {
			setFocusable(true);
			setClickable(true);
			setLongClickable(true);
		} else {
			setFocusable(false);
			setClickable(false);
			setLongClickable(false);
		}
	}

	/**
	 * Returns the top padding of the view, plus space for the top Drawable if any.
	 */
	public int getCompoundPaddingTop() {
		return getPaddingTop();
	}

	/**
	 * Returns the bottom padding of the view, plus space for the bottom Drawable if any.
	 */
	public int getCompoundPaddingBottom() {
		return getPaddingBottom();
	}

	/**
	 * Returns the left padding of the view, plus space for the left Drawable if any.
	 */
	public int getCompoundPaddingLeft() {
		return getPaddingLeft();
	}

	/**
	 * Returns the right padding of the view, plus space for the right Drawable if any.
	 */
	public int getCompoundPaddingRight() {
		return getPaddingRight();
	}

	/**
	 * Returns the start padding of the view, plus space for the start Drawable if any.
	 */
	public int getCompoundPaddingStart() {
		switch (getLayoutDirection()) {
		default:
		case LAYOUT_DIRECTION_LTR:
			return getCompoundPaddingLeft();
		case LAYOUT_DIRECTION_RTL:
			return getCompoundPaddingRight();
		}
	}

	/**
	 * Returns the end padding of the view, plus space for the end Drawable if any.
	 */
	public int getCompoundPaddingEnd() {
		switch (getLayoutDirection()) {
		default:
		case LAYOUT_DIRECTION_LTR:
			return getCompoundPaddingRight();
		case LAYOUT_DIRECTION_RTL:
			return getCompoundPaddingLeft();
		}
	}

	/**
	 * Returns the extended top padding of the view, including both the top Drawable if any and any extra space to keep
	 * more than maxLines of text from showing. It is only valid to call this after measuring.
	 */
	public int getExtendedPaddingTop() {
		if (mMaxMode != LINES) {
			return getCompoundPaddingTop();
		}

		if (mLayout.getLineCount() <= mMaximum) {
			return getCompoundPaddingTop();
		}

		int top = getCompoundPaddingTop();
		int bottom = getCompoundPaddingBottom();
		int viewht = getHeight() - top - bottom;
		int layoutht = mLayout.getLineTop(mMaximum);

		if (layoutht >= viewht) {
			return top;
		}

		final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
		if (gravity == Gravity.TOP) {
			return top;
		} else if (gravity == Gravity.BOTTOM) {
			return top + viewht - layoutht;
		} else { // (gravity == Gravity.CENTER_VERTICAL)
			return top + (viewht - layoutht) / 2;
		}
	}

	/**
	 * Returns the extended bottom padding of the view, including both the bottom Drawable if any and any extra space to
	 * keep more than maxLines of text from showing. It is only valid to call this after measuring.
	 */
	public int getExtendedPaddingBottom() {
		if (mMaxMode != LINES) {
			return getCompoundPaddingBottom();
		}

		if (mLayout.getLineCount() <= mMaximum) {
			return getCompoundPaddingBottom();
		}

		int top = getCompoundPaddingTop();
		int bottom = getCompoundPaddingBottom();
		int viewht = getHeight() - top - bottom;
		int layoutht = mLayout.getLineTop(mMaximum);

		if (layoutht >= viewht) {
			return bottom;
		}

		final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
		if (gravity == Gravity.TOP) {
			return bottom + viewht - layoutht;
		} else if (gravity == Gravity.BOTTOM) {
			return bottom;
		} else { // (gravity == Gravity.CENTER_VERTICAL)
			return bottom + (viewht - layoutht) / 2;
		}
	}

	/**
	 * Returns the total left padding of the view, including the left Drawable if any.
	 */
	public int getTotalPaddingLeft() {
		return getCompoundPaddingLeft();
	}

	/**
	 * Returns the total right padding of the view, including the right Drawable if any.
	 */
	public int getTotalPaddingRight() {
		return getCompoundPaddingRight();
	}

	/**
	 * Returns the total start padding of the view, including the start Drawable if any.
	 */
	public int getTotalPaddingStart() {
		return getCompoundPaddingStart();
	}

	/**
	 * Returns the total end padding of the view, including the end Drawable if any.
	 */
	public int getTotalPaddingEnd() {
		return getCompoundPaddingEnd();
	}

	/**
	 * Returns the total top padding of the view, including the top Drawable if any, the extra space to keep more than
	 * maxLines from showing, and the vertical offset for gravity, if any.
	 */
	public int getTotalPaddingTop() {
		return getExtendedPaddingTop() + getVerticalOffset(true);
	}

	/**
	 * Returns the total bottom padding of the view, including the bottom Drawable if any, the extra space to keep more
	 * than maxLines from showing, and the vertical offset for gravity, if any.
	 */
	public int getTotalPaddingBottom() {
		return getExtendedPaddingBottom() + getBottomVerticalOffset(true);
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		if (left != getPaddingLeft() || right != getPaddingRight() || top != getPaddingTop()
				|| bottom != getPaddingBottom()) {
			nullLayouts();
		}

		// the super call will requestLayout()
		super.setPadding(left, top, right, bottom);
		invalidate();
	}

	@Override
	public void setPaddingRelative(int start, int top, int end, int bottom) {
		if (start != getPaddingStart() || end != getPaddingEnd() || top != getPaddingTop()
				|| bottom != getPaddingBottom()) {
			nullLayouts();
		}

		// the super call will requestLayout()
		super.setPaddingRelative(start, top, end, bottom);
		invalidate();
	}

	/**
	 * Sets the text color, size, style, hint color, and highlight color from the specified TextAppearance resource.
	 */
	public void setTextAppearance(Context context, int resid) {
		ColorStateList colors = ColorStateList.valueOf(Color.BLACK);
		int ts = 20;
		setTextColor(colors);
		setRawTextSize(ts);
		setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
	}

	/**
	 * Get the default {@link Locale} of the text in this TextView.
	 * 
	 * @return the default {@link Locale} of the text in this TextView.
	 */
	public Locale getTextLocale() {
		return mTextPaint.getTextLocale();
	}

	/**
	 * Set the default {@link Locale} of the text in this TextView to the given value. This value is used to choose
	 * appropriate typefaces for ambiguous characters. Typically used for CJK locales to disambiguate Hanzi/Kanji/Hanja
	 * characters.
	 * 
	 * @param locale
	 *            the {@link Locale} for drawing text, must not be }
	 * 
	 *            /**
	 * @return the size (in pixels) of the default text size in this TextView.
	 */
	@ViewDebug.ExportedProperty(category = "text")
	public float getTextSize() {
		return mTextPaint.getTextSize();
	}

	/**
	 * Set the default text size to the given value, interpreted as "scaled pixel" units. This size is adjusted based on
	 * the current density and user font size preference.
	 * 
	 * @param size
	 *            The scaled pixel size.
	 * 
	 * @attr ref android.R.styleable#TextView_textSize
	 */
	public void setTextSize(float size) {
		setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
	}

	/**
	 * Set the default text size to a given unit and value. See {@link TypedValue} for the possible dimension units.
	 * 
	 * @param unit
	 *            The desired dimension unit.
	 * @param size
	 *            The desired size in the given units.
	 * 
	 * @attr ref android.R.styleable#TextView_textSize
	 */
	public void setTextSize(int unit, float size) {
		Context c = getContext();
		Resources r;

		if (c == null)
			r = Resources.getSystem();
		else
			r = c.getResources();

		setRawTextSize(TypedValue.applyDimension(unit, size, r.getDisplayMetrics()));
	}

	private void setRawTextSize(float size) {
		if (size != mTextPaint.getTextSize()) {
			mTextPaint.setTextSize(size);

			if (mLayout != null) {
				nullLayouts();
				requestLayout();
				invalidate();
			}
		}
	}

	/**
	 * @return the extent by which text is currently being stretched horizontally. This will usually be 1.
	 */
	public float getTextScaleX() {
		return mTextPaint.getTextScaleX();
	}

	/**
	 * Sets the extent by which text should be stretched horizontally.
	 * 
	 * @attr ref android.R.styleable#TextView_textScaleX
	 */
	public void setTextScaleX(float size) {
		if (size != mTextPaint.getTextScaleX()) {
			mUserSetTextScaleX = true;
			mTextPaint.setTextScaleX(size);

			if (mLayout != null) {
				nullLayouts();
				requestLayout();
				invalidate();
			}
		}
	}

	/**
	 * Sets the typeface and style in which the text should be displayed. Note that not all Typeface families actually
	 * have bold and italic variants, so you may need to use {@link #setTypeface(Typeface, int)} to get the appearance
	 * that you actually want.
	 * 
	 * @see #getTypeface()
	 * 
	 * @attr ref android.R.styleable#TextView_fontFamily
	 * @attr ref android.R.styleable#TextView_typeface
	 * @attr ref android.R.styleable#TextView_textStyle
	 */
	public void setTypeface(Typeface tf) {
		if (mTextPaint.getTypeface() != tf) {
			mTextPaint.setTypeface(tf);

			if (mLayout != null) {
				nullLayouts();
				requestLayout();
				invalidate();
			}
		}
	}

	/**
	 * @return the current typeface and style in which the text is being displayed.
	 * 
	 * @see #setTypeface(Typeface)
	 * 
	 * @attr ref android.R.styleable#TextView_fontFamily
	 * @attr ref android.R.styleable#TextView_typeface
	 * @attr ref android.R.styleable#TextView_textStyle
	 */
	public Typeface getTypeface() {
		return mTextPaint.getTypeface();
	}

	/**
	 * Sets the text color for all the states (normal, selected, focused) to be this color.
	 * 
	 * @see #setTextColor(ColorStateList)
	 * @see #getTextColors()
	 * 
	 * @attr ref android.R.styleable#TextView_textColor
	 */
	public void setTextColor(int color) {
		mTextColor = ColorStateList.valueOf(color);
		updateTextColors();
	}

	/**
	 * Sets the text color.
	 * 
	 * @see #setTextColor(int)
	 * @see #getTextColors()
	 * @see #setHintTextColor(ColorStateList)
	 * @see #setLinkTextColor(ColorStateList)
	 * 
	 * @attr ref android.R.styleable#TextView_textColor
	 */
	public void setTextColor(ColorStateList colors) {
		if (colors == null) {
			throw new NullPointerException();
		}

		mTextColor = colors;
		updateTextColors();
	}

	/**
	 * Gets the text colors for the different states (normal, selected, focused) of the TextView.
	 * 
	 * @see #setTextColor(ColorStateList)
	 * @see #setTextColor(int)
	 * 
	 * @attr ref android.R.styleable#TextView_textColor
	 */
	public final ColorStateList getTextColors() {
		return mTextColor;
	}

	/**
	 * <p>
	 * Return the current color selected for normal text.
	 * </p>
	 * 
	 * @return Returns the current text color.
	 */
	public final int getCurrentTextColor() {
		return mCurTextColor;
	}

	/**
	 * Sets the color used to display the selection highlight.
	 * 
	 * @attr ref android.R.styleable#TextView_textColorHighlight
	 */
	public void setHighlightColor(int color) {
		if (mHighlightColor != color) {
			mHighlightColor = color;
			invalidate();
		}
	}

	/**
	 * @return the color used to display the selection highlight
	 * 
	 * @see #setHighlightColor(int)
	 * 
	 * @attr ref android.R.styleable#TextView_textColorHighlight
	 */
	public int getHighlightColor() {
		return mHighlightColor;
	}

	/**
	 * Sets whether the soft input method will be made visible when this TextView gets focused. The default is true.
	 * 
	 * @hide
	 */
	public final void setShowSoftInputOnFocus(boolean show) {
		mEditor.mShowSoftInputOnFocus = show;
	}

	/**
	 * Returns whether the soft input method will be made visible when this TextView gets focused. The default is true.
	 * 
	 * @hide
	 */
	public final boolean getShowSoftInputOnFocus() {
		// When there is no Editor, return default true value
		return false || mEditor.mShowSoftInputOnFocus;
	}

	/**
	 * Gives the text a shadow of the specified radius and color, the specified distance from its normal position.
	 * 
	 * @attr ref android.R.styleable#TextView_shadowColor
	 * @attr ref android.R.styleable#TextView_shadowDx
	 * @attr ref android.R.styleable#TextView_shadowDy
	 * @attr ref android.R.styleable#TextView_shadowRadius
	 */
	public void setShadowLayer(float radius, float dx, float dy, int color) {
		mTextPaint.setShadowLayer(radius, dx, dy, color);

		mShadowRadius = radius;
		mShadowDx = dx;
		mShadowDy = dy;

		// Will change text clip region
		// // mEditor.invalidateTextDisplayList();
		invalidate();
	}

	/**
	 * Gets the radius of the shadow layer.
	 * 
	 * @return the radius of the shadow layer. If 0, the shadow layer is not visible
	 * 
	 * @see #setShadowLayer(float, float, float, int)
	 * 
	 * @attr ref android.R.styleable#TextView_shadowRadius
	 */
	public float getShadowRadius() {
		return mShadowRadius;
	}

	/**
	 * @return the horizontal offset of the shadow layer
	 * 
	 * @see #setShadowLayer(float, float, float, int)
	 * 
	 * @attr ref android.R.styleable#TextView_shadowDx
	 */
	public float getShadowDx() {
		return mShadowDx;
	}

	/**
	 * @return the vertical offset of the shadow layer
	 * 
	 * @see #setShadowLayer(float, float, float, int)
	 * 
	 * @attr ref android.R.styleable#TextView_shadowDy
	 */
	public float getShadowDy() {
		return mShadowDy;
	}

	/**
	 * @return the base paint used for the text. Please use this only to consult the Paint's properties and not to
	 *         change them.
	 */
	public TextPaint getPaint() {
		return mTextPaint;
	}

	/**
	 * Returns the list of URLSpans attached to the text (by {@link Linkify} or otherwise) if any. You can call
	 * {@link URLSpan#getURL} on them to find where they link to or use {@link Spanned#getSpanStart} and
	 * {@link Spanned#getSpanEnd} to find the region of the text they are attached to.
	 */
	public URLSpan[] getUrls() {
		if (mText instanceof Spanned) {
			return ((Spanned) mText).getSpans(0, mText.length(), URLSpan.class);
		} else {
			return new URLSpan[0];
		}
	}

	/**
	 * Sets the color of links in the text.
	 * 
	 * @see #setLinkTextColor(ColorStateList)
	 * @see #getLinkTextColors()
	 * 
	 * @attr ref android.R.styleable#TextView_textColorLink
	 */
	public final void setLinkTextColor(int color) {
		mLinkTextColor = ColorStateList.valueOf(color);
		updateTextColors();
	}

	/**
	 * Sets the color of links in the text.
	 * 
	 * @see #setLinkTextColor(int)
	 * @see #getLinkTextColors()
	 * @see #setTextColor(ColorStateList)
	 * @see #setHintTextColor(ColorStateList)
	 * 
	 * @attr ref android.R.styleable#TextView_textColorLink
	 */
	public final void setLinkTextColor(ColorStateList colors) {
		mLinkTextColor = colors;
		updateTextColors();
	}

	/**
	 * @return the list of colors used to paint the links in the text, for the different states of this TextView
	 * 
	 * @see #setLinkTextColor(ColorStateList)
	 * @see #setLinkTextColor(int)
	 * 
	 * @attr ref android.R.styleable#TextView_textColorLink
	 */
	public final ColorStateList getLinkTextColors() {
		return mLinkTextColor;
	}

	/**
	 * Sets the horizontal alignment of the text and the vertical gravity that will be used when there is extra space in
	 * the TextView beyond what is required for the text itself.
	 * 
	 * @see android.view.Gravity
	 * @attr ref android.R.styleable#TextView_gravity
	 */
	public void setGravity(int gravity) {
		if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
			gravity |= Gravity.START;
		}
		if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
			gravity |= Gravity.TOP;
		}

		boolean newLayout = false;

		if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) != (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
			newLayout = true;
		}

		if (gravity != mGravity) {
			invalidate();
			mLayoutAlignment = null;
		}

		mGravity = gravity;

		if (mLayout != null && newLayout) {
			// XXX this is heavy-handed because no actual content changes.
			int want = mLayout.getWidth();
			int hintWant = 0;

			makeNewLayout(want, hintWant, UNKNOWN_BORING, UNKNOWN_BORING, getRight() - getLeft()
					- getCompoundPaddingLeft() - getCompoundPaddingRight(), true);
		}
	}

	/**
	 * Returns the horizontal and vertical alignment of this TextView.
	 * 
	 * @see android.view.Gravity
	 * @attr ref android.R.styleable#TextView_gravity
	 */
	public int getGravity() {
		return mGravity;
	}

	/**
	 * @return the flags on the Paint being used to display the text.
	 * @see Paint#getFlags
	 */
	public int getPaintFlags() {
		return mTextPaint.getFlags();
	}

	/**
	 * Sets flags on the Paint being used to display the text and reflows the text if they are different from the old
	 * flags.
	 * 
	 * @see Paint#setFlags
	 */
	public void setPaintFlags(int flags) {
		if (mTextPaint.getFlags() != flags) {
			mTextPaint.setFlags(flags);

			if (mLayout != null) {
				nullLayouts();
				requestLayout();
				invalidate();
			}
		}
	}

	/**
	 * Sets whether the text should be allowed to be wider than the View is. If false, it will be wrapped to the width
	 * of the View.
	 * 
	 * @attr ref android.R.styleable#TextView_scrollHorizontally
	 */
	public void setHorizontallyScrolling(boolean whether) {
		if (mHorizontallyScrolling != whether) {
			mHorizontallyScrolling = whether;

			if (mLayout != null) {
				nullLayouts();
				requestLayout();
				invalidate();
			}
		}
	}

	/**
	 * Returns whether the text is allowed to be wider than the View is. If false, the text will be wrapped to the width
	 * of the View.
	 * 
	 * @attr ref android.R.styleable#TextView_scrollHorizontally
	 * @hide
	 */
	public boolean getHorizontallyScrolling() {
		return mHorizontallyScrolling;
	}

	/**
	 * Makes the TextView at least this many lines tall.
	 * 
	 * Setting this value overrides any other (minimum) height setting. A single line TextView will set this value to 1.
	 * 
	 * @see #getMinLines()
	 * 
	 * @attr ref android.R.styleable#TextView_minLines
	 */
	public void setMinLines(int minlines) {
		mMinimum = minlines;
		mMinMode = LINES;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the minimum number of lines displayed in this TextView, or -1 if the minimum height was set in pixels
	 *         instead using {@link #setMinHeight(int) or #setHeight(int)}.
	 * 
	 * @see #setMinLines(int)
	 * 
	 * @attr ref android.R.styleable#TextView_minLines
	 */
	public int getMinLines() {
		return mMinMode == LINES ? mMinimum : -1;
	}

	/**
	 * Makes the TextView at least this many pixels tall.
	 * 
	 * Setting this value overrides any other (minimum) number of lines setting.
	 * 
	 * @attr ref android.R.styleable#TextView_minHeight
	 */
	public void setMinHeight(int minHeight) {
		mMinimum = minHeight;
		mMinMode = PIXELS;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the minimum height of this TextView expressed in pixels, or -1 if the minimum height was set in number of
	 *         lines instead using {@link #setMinLines(int) or #setLines(int)}.
	 * 
	 * @see #setMinHeight(int)
	 * 
	 * @attr ref android.R.styleable#TextView_minHeight
	 */
	public int getMinHeight() {
		return mMinMode == PIXELS ? mMinimum : -1;
	}

	/**
	 * Makes the TextView at most this many lines tall.
	 * 
	 * Setting this value overrides any other (maximum) height setting.
	 * 
	 * @attr ref android.R.styleable#TextView_maxLines
	 */
	public void setMaxLines(int maxlines) {
		mMaximum = maxlines;
		mMaxMode = LINES;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the maximum number of lines displayed in this TextView, or -1 if the maximum height was set in pixels
	 *         instead using {@link #setMaxHeight(int) or #setHeight(int)}.
	 * 
	 * @see #setMaxLines(int)
	 * 
	 * @attr ref android.R.styleable#TextView_maxLines
	 */
	public int getMaxLines() {
		return mMaxMode == LINES ? mMaximum : -1;
	}

	/**
	 * Makes the TextView at most this many pixels tall. This option is mutually exclusive with the
	 * {@link #setMaxLines(int)} method.
	 * 
	 * Setting this value overrides any other (maximum) number of lines setting.
	 * 
	 * @attr ref android.R.styleable#TextView_maxHeight
	 */
	public void setMaxHeight(int maxHeight) {
		mMaximum = maxHeight;
		mMaxMode = PIXELS;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the maximum height of this TextView expressed in pixels, or -1 if the maximum height was set in number of
	 *         lines instead using {@link #setMaxLines(int) or #setLines(int)}.
	 * 
	 * @see #setMaxHeight(int)
	 * 
	 * @attr ref android.R.styleable#TextView_maxHeight
	 */
	public int getMaxHeight() {
		return mMaxMode == PIXELS ? mMaximum : -1;
	}

	/**
	 * Makes the TextView exactly this many lines tall.
	 * 
	 * Note that setting this value overrides any other (minimum / maximum) number of lines or height setting. A single
	 * line TextView will set this value to 1.
	 * 
	 * @attr ref android.R.styleable#TextView_lines
	 */
	public void setLines(int lines) {
		mMaximum = mMinimum = lines;
		mMaxMode = mMinMode = LINES;

		requestLayout();
		invalidate();
	}

	/**
	 * Makes the TextView exactly this many pixels tall. You could do the same thing by specifying this number in the
	 * LayoutParams.
	 * 
	 * Note that setting this value overrides any other (minimum / maximum) number of lines or height setting.
	 * 
	 * @attr ref android.R.styleable#TextView_height
	 */
	public void setHeight(int pixels) {
		mMaximum = mMinimum = pixels;
		mMaxMode = mMinMode = PIXELS;

		requestLayout();
		invalidate();
	}

	/**
	 * Makes the TextView at least this many ems wide
	 * 
	 * @attr ref android.R.styleable#TextView_minEms
	 */
	public void setMinEms(int minems) {
		mMinWidth = minems;
		mMinWidthMode = EMS;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the minimum width of the TextView, expressed in ems or -1 if the minimum width was set in pixels instead
	 *         (using {@link #setMinWidth(int)} or {@link #setWidth(int)}).
	 * 
	 * @see #setMinEms(int)
	 * @see #setEms(int)
	 * 
	 * @attr ref android.R.styleable#TextView_minEms
	 */
	public int getMinEms() {
		return mMinWidthMode == EMS ? mMinWidth : -1;
	}

	/**
	 * Makes the TextView at least this many pixels wide
	 * 
	 * @attr ref android.R.styleable#TextView_minWidth
	 */
	public void setMinWidth(int minpixels) {
		mMinWidth = minpixels;
		mMinWidthMode = PIXELS;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the minimum width of the TextView, in pixels or -1 if the minimum width was set in ems instead (using
	 *         {@link #setMinEms(int)} or {@link #setEms(int)}).
	 * 
	 * @see #setMinWidth(int)
	 * @see #setWidth(int)
	 * 
	 * @attr ref android.R.styleable#TextView_minWidth
	 */
	public int getMinWidth() {
		return mMinWidthMode == PIXELS ? mMinWidth : -1;
	}

	/**
	 * Makes the TextView at most this many ems wide
	 * 
	 * @attr ref android.R.styleable#TextView_maxEms
	 */
	public void setMaxEms(int maxems) {
		mMaxWidth = maxems;
		mMaxWidthMode = EMS;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the maximum width of the TextView, expressed in ems or -1 if the maximum width was set in pixels instead
	 *         (using {@link #setMaxWidth(int)} or {@link #setWidth(int)}).
	 * 
	 * @see #setMaxEms(int)
	 * @see #setEms(int)
	 * 
	 * @attr ref android.R.styleable#TextView_maxEms
	 */
	public int getMaxEms() {
		return mMaxWidthMode == EMS ? mMaxWidth : -1;
	}

	/**
	 * Makes the TextView at most this many pixels wide
	 * 
	 * @attr ref android.R.styleable#TextView_maxWidth
	 */
	public void setMaxWidth(int maxpixels) {
		mMaxWidth = maxpixels;
		mMaxWidthMode = PIXELS;

		requestLayout();
		invalidate();
	}

	/**
	 * @return the maximum width of the TextView, in pixels or -1 if the maximum width was set in ems instead (using
	 *         {@link #setMaxEms(int)} or {@link #setEms(int)}).
	 * 
	 * @see #setMaxWidth(int)
	 * @see #setWidth(int)
	 * 
	 * @attr ref android.R.styleable#TextView_maxWidth
	 */
	public int getMaxWidth() {
		return mMaxWidthMode == PIXELS ? mMaxWidth : -1;
	}

	/**
	 * Makes the TextView exactly this many ems wide
	 * 
	 * @see #setMaxEms(int)
	 * @see #setMinEms(int)
	 * @see #getMinEms()
	 * @see #getMaxEms()
	 * 
	 * @attr ref android.R.styleable#TextView_ems
	 */
	public void setEms(int ems) {
		mMaxWidth = mMinWidth = ems;
		mMaxWidthMode = mMinWidthMode = EMS;

		requestLayout();
		invalidate();
	}

	/**
	 * Makes the TextView exactly this many pixels wide. You could do the same thing by specifying this number in the
	 * LayoutParams.
	 * 
	 * @see #setMaxWidth(int)
	 * @see #setMinWidth(int)
	 * @see #getMinWidth()
	 * @see #getMaxWidth()
	 * 
	 * @attr ref android.R.styleable#TextView_width
	 */
	public void setWidth(int pixels) {
		mMaxWidth = mMinWidth = pixels;
		mMaxWidthMode = mMinWidthMode = PIXELS;

		requestLayout();
		invalidate();
	}

	/**
	 * Sets line spacing for this TextView. Each line will have its height multiplied by <code>mult</code> and have
	 * <code>add</code> added to it.
	 * 
	 * @attr ref android.R.styleable#TextView_lineSpacingExtra
	 * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
	 */
	public void setLineSpacing(float add, float mult) {
		if (mSpacingAdd != add || mSpacingMult != mult) {
			mSpacingAdd = add;
			mSpacingMult = mult;

			if (mLayout != null) {
				nullLayouts();
				requestLayout();
				invalidate();
			}
		}
	}

	/**
	 * Gets the line spacing multiplier
	 * 
	 * @return the value by which each line's height is multiplied to get its actual height.
	 * 
	 * @see #setLineSpacing(float, float)
	 * @see #getLineSpacingExtra()
	 * 
	 * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
	 */
	public float getLineSpacingMultiplier() {
		return mSpacingMult;
	}

	/**
	 * Gets the line spacing extra space
	 * 
	 * @return the extra space that is added to the height of each lines of this TextView.
	 * 
	 * @see #setLineSpacing(float, float)
	 * @see #getLineSpacingMultiplier()
	 * 
	 * @attr ref android.R.styleable#TextView_lineSpacingExtra
	 */
	public float getLineSpacingExtra() {
		return mSpacingAdd;
	}

	/**
	 * Convenience method: Append the specified text to the TextView's display buffer, upgrading it to
	 * BufferType.EDITABLE if it was not already editable.
	 */
	public final void append(CharSequence text) {
		append(text, 0, text.length());
	}

	/**
	 * Convenience method: Append the specified text slice to the TextView's display buffer, upgrading it to
	 * BufferType.EDITABLE if it was not already editable.
	 */
	public void append(CharSequence text, int start, int end) {
		if (!(mText instanceof Editable)) {
			setText(mText);
		}

		((Editable) mText).append(text, start, end);
	}

	private void updateTextColors() {
		boolean inval = false;
		int color = mTextColor.getColorForState(getDrawableState(), 0);
		if (color != mCurTextColor) {
			mCurTextColor = color;
			inval = true;
		}
		if (mLinkTextColor != null) {
			color = mLinkTextColor.getColorForState(getDrawableState(), 0);
			if (color != mTextPaint.linkColor) {
				mTextPaint.linkColor = color;
				inval = true;
			}
		}
		if (inval) {
			// Text needs to be redrawn with the new color
			// // mEditor.invalidateTextDisplayList();
			invalidate();
		}
	}

	void removeMisspelledSpans(Spannable spannable) {
		SuggestionSpan[] suggestionSpans = spannable.getSpans(0, spannable.length(), SuggestionSpan.class);
		for (int i = 0; i < suggestionSpans.length; i++) {
			int flags = suggestionSpans[i].getFlags();
			if ((flags & SuggestionSpan.FLAG_EASY_CORRECT) != 0 && (flags & SuggestionSpan.FLAG_MISSPELLED) != 0) {
				spannable.removeSpan(suggestionSpans[i]);
			}
		}
	}

	/**
	 * Control whether this text view saves its entire text contents when freezing to an icicle, in addition to dynamic
	 * state such as cursor position. By default this is false, not saving the text. Set to true if the text in the text
	 * view is not being saved somewhere else in persistent storage (such as in a content provider) so that if the view
	 * is later thawed the user will not lose their data.
	 * 
	 * @param freezesText
	 *            Controls whether a frozen icicle should include the entire text data: true to include it, false to
	 *            not.
	 * 
	 * @attr ref android.R.styleable#TextView_freezesText
	 */
	public void setFreezesText(boolean freezesText) {
		mFreezesText = freezesText;
	}

	/**
	 * Return whether this text view is including its entire text contents in frozen icicles.
	 * 
	 * @return Returns true if text is included, false if it isn't.
	 * 
	 * @see #setFreezesText
	 */
	public boolean getFreezesText() {
		return mFreezesText;
	}

	// /////////////////////////////////////////////////////////////////////////

	/**
	 * Sets the Factory used to create new Editables.
	 */
	public final void setEditableFactory(Editable.Factory factory) {
		mEditableFactory = factory;
		setText(mText);
	}

	/**
	 * Sets the text that this TextView is to display (see {@link #setText(CharSequence)}) and also sets whether it is
	 * stored in a styleable/spannable buffer and whether it is editable.
	 * 
	 * @attr ref android.R.styleable#TextView_text
	 * @attr ref android.R.styleable#TextView_bufferType
	 */
	public void setText(CharSequence text) {
		Log.v("setText", text.toString());
		setText(text, true, 0);
	}

	private void setText(CharSequence text, boolean notifyBefore, int oldlen) {
		if (text == null) {
			text = "";
		}

		// If suggestions are not enabled, remove the suggestion spans from the text
		// if (!isSuggestionsEnabled()) {
		// text = removeSuggestionSpans(text);
		// }

		if (!mUserSetTextScaleX)
			mTextPaint.setTextScaleX(1.0f);

		if (notifyBefore) {
			if (mText != null) {
				oldlen = mText.length();
				sendBeforeTextChanged(mText, 0, oldlen, text.length());
			} else {
				sendBeforeTextChanged("", 0, 0, text.length());
			}
		}

		boolean needEditableForNotification = false;

		if (mListeners != null && mListeners.size() != 0) {
			needEditableForNotification = true;
		}

		if (getKeyListener() != null || needEditableForNotification) {
			// TODO L.A.H. do not create new editable!
			Editable t = mEditableFactory.newEditable(text);
			text = t;
			InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null)
				imm.restartInput(this);
		}

		mText = text;
		mTransformed = text;
		// if (mTransformation == null) {
		// mTransformed = text;
		// } else {
		// mTransformed = mTransformation.getTransformation(text, this);
		// }

		final int textLength = text.length();

		if (text instanceof Spannable /* && !mAllowTransformationLengthChange */) {
			Spannable sp = (Spannable) text;

			// Remove any ChangeWatchers that might have come from other TextViews.
			final ChangeWatcher[] watchers = sp.getSpans(0, sp.length(), ChangeWatcher.class);
			final int count = watchers.length;
			for (int i = 0; i < count; i++) {
				sp.removeSpan(watchers[i]);
			}

			if (mChangeWatcher == null)
				mChangeWatcher = new ChangeWatcher();

			sp.setSpan(mChangeWatcher, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE
					| (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));

			mEditor.addSpanWatchers(sp);

			// if (mTransformation != null) {
			// sp.setSpan(mTransformation, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
			// }

			// if (mMovement != null) {
			// mMovement.initialize(this, (Spannable) text);
			//
			// /*
			// * Initializing the movement method will have set the selection, so reset mSelectionMoved to keep that
			// * from interfering with the normal on-focus selection-setting.
			// */
			// // mEditor.mSelectionMoved = false;
			// }
		}

		if (mLayout != null) {
			checkForRelayout();
		}

		sendOnTextChanged(text, 0, oldlen, textLength);
		onTextChanged(text, 0, oldlen, textLength);

		if (needEditableForNotification) {
			sendAfterTextChanged((Editable) text);
		}

		// SelectionModifierCursorController depends on textCanBeSelected, which depends on text
		mEditor.prepareCursorControllers();
	}

	/**
	 * Like {@link #setText(CharSequence)}, except that the cursor position (if any) is retained in the new text.
	 */
	public final void setTextKeepState(CharSequence text) {
		int start = getSelectionStart();
		int end = getSelectionEnd();
		int len = text.length();

		setText(text);

		if (start >= 0 || end >= 0) {
			if (mText instanceof Spannable) {
				Selection.setSelection((Spannable) mText, Math.max(0, Math.min(start, len)),
						Math.max(0, Math.min(end, len)));
			}
		}
	}

	/**
	 * Set the type of the content with a constant as defined for {@link EditorInfo#inputType}. This will take care of
	 * changing the key listener, by calling {@link #setKeyListener(KeyListener)}, to match the given content type. If
	 * the given content type is {@link EditorInfo#TYPE_NULL} then a soft keyboard will not be displayed for this text
	 * view.
	 * 
	 * Note that the maximum number of displayed lines (see {@link #setMaxLines(int)}) will be modified if you change
	 * the {@link EditorInfo#TYPE_TEXT_FLAG_MULTI_LINE} flag of the input type.
	 * 
	 * @see #getInputType()
	 * @see #setRawInputType(int)
	 * @see android.text.InputType
	 * @attr ref android.R.styleable#TextView_inputType
	 */
	public void setInputType(int type) {
		setInputType(type, false);
		// if (!isSuggestionsEnabled()) {
		// mText = removeSuggestionSpans(mText);
		// }
		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.restartInput(this);
	}

	/**
	 * Directly change the content type integer of the text view, without modifying any other state.
	 * 
	 * @see #setInputType(int)
	 * @see android.text.InputType
	 * @attr ref android.R.styleable#TextView_inputType
	 */
	public void setRawInputType(int type) {
		mEditor.mInputType = type;
	}

	private void setInputType(int type, boolean direct) {
		final int cls = type & EditorInfo.TYPE_MASK_CLASS;
		KeyListener input;
		if (cls == EditorInfo.TYPE_CLASS_TEXT) {
			boolean autotext = (type & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0;
			TextKeyListener.Capitalize cap;
			if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
				cap = TextKeyListener.Capitalize.CHARACTERS;
			} else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
				cap = TextKeyListener.Capitalize.WORDS;
			} else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
				cap = TextKeyListener.Capitalize.SENTENCES;
			} else {
				cap = TextKeyListener.Capitalize.NONE;
			}
			input = TextKeyListener.getInstance(autotext, cap);
		} else if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
			input = DigitsKeyListener.getInstance((type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0,
					(type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
		} else if (cls == EditorInfo.TYPE_CLASS_DATETIME) {
			switch (type & EditorInfo.TYPE_MASK_VARIATION) {
			case EditorInfo.TYPE_DATETIME_VARIATION_DATE:
				input = DateKeyListener.getInstance();
				break;
			case EditorInfo.TYPE_DATETIME_VARIATION_TIME:
				input = TimeKeyListener.getInstance();
				break;
			default:
				input = DateTimeKeyListener.getInstance();
				break;
			}
		} else if (cls == EditorInfo.TYPE_CLASS_PHONE) {
			input = DialerKeyListener.getInstance();
		} else {
			input = TextKeyListener.getInstance();
		}
		setRawInputType(type);
		if (direct) {
			mEditor.mKeyListener = input;
		} else {
			setKeyListenerOnly(input);
		}
	}

	/**
	 * Get the type of the editable content.
	 * 
	 * @see #setInputType(int)
	 * @see android.text.InputType
	 */
	public int getInputType() {
		return mEditor.mInputType;
	}

	/**
	 * Change the custom IME action associated with the text view, which will be reported to an IME with
	 * {@link EditorInfo#actionLabel} and {@link EditorInfo#actionId} when it has focus.
	 * 
	 * @see #getImeActionLabel
	 * @see #getImeActionId
	 * @see android.view.inputmethod.EditorInfo
	 * @attr ref android.R.styleable#TextView_imeActionLabel
	 * @attr ref android.R.styleable#TextView_imeActionId
	 */
	public void setImeActionLabel(CharSequence label, int actionId) {
		mEditor.mInputContentType.imeActionLabel = label;
		mEditor.mInputContentType.imeActionId = actionId;
	}

	/**
	 * Get the IME action label previous set with {@link #setImeActionLabel}.
	 * 
	 * @see #setImeActionLabel
	 * @see android.view.inputmethod.EditorInfo
	 */
	public CharSequence getImeActionLabel() {
		return mEditor.mInputContentType != null ? mEditor.mInputContentType.imeActionLabel : null;
	}

	/**
	 * Get the IME action ID previous set with {@link #setImeActionLabel}.
	 * 
	 * @see #setImeActionLabel
	 * @see android.view.inputmethod.EditorInfo
	 */
	public int getImeActionId() {
		return mEditor.mInputContentType != null ? mEditor.mInputContentType.imeActionId : 0;
	}

	/**
	 * Set a special listener to be called when an action is performed on the text view. This will be called when the
	 * enter key is pressed, or when an action supplied to the IME is selected by the user. Setting this means that the
	 * normal hard key event will not insert a newline into the text view, even if it is multi-line; holding down the
	 * ALT modifier will, however, allow the user to insert a newline character.
	 */
	public void setOnEditorActionListener(OnEditorActionListener l) {
		mEditor.mInputContentType.onEditorActionListener = l;
	}

	/**
	 * Called when an attached input method calls {@link InputConnection#performEditorAction(int)
	 * InputConnection.performEditorAction()} for this text view. The default implementation will call your action
	 * listener supplied to {@link #setOnEditorActionListener}, or perform a standard operation for
	 * {@link EditorInfo#IME_ACTION_NEXT EditorInfo.IME_ACTION_NEXT}, {@link EditorInfo#IME_ACTION_PREVIOUS
	 * EditorInfo.IME_ACTION_PREVIOUS}, or {@link EditorInfo#IME_ACTION_DONE EditorInfo.IME_ACTION_DONE}.
	 * 
	 * <p>
	 * For backwards compatibility, if no IME options have been set and the text view would not normally advance focus
	 * on enter, then the NEXT and DONE actions received here will be turned into an enter key down/up pair to go
	 * through the normal key handling.
	 * 
	 * @param actionCode
	 *            The code of the action being performed.
	 * 
	 * @see #setOnEditorActionListener
	 */
	public void onEditorAction(int actionCode) {
		final Editor.InputContentType ict = mEditor.mInputContentType;
		if (ict != null) {
			if (ict.onEditorActionListener != null) {
				if (ict.onEditorActionListener.onEditorAction(this, actionCode, null)) {
					return;
				}
			}

			// This is the handling for some default action.
			// Note that for backwards compatibility we don't do this
			// default handling if explicit ime options have not been given,
			// instead turning this into the normal enter key codes that an
			// app may be expecting.
			if (actionCode == EditorInfo.IME_ACTION_NEXT) {
				View v = focusSearch(FOCUS_FORWARD);
				if (v != null) {
					if (!v.requestFocus(FOCUS_FORWARD)) {
						throw new IllegalStateException("focus search returned a view "
								+ "that wasn't able to take focus!");
					}
				}
				return;

			} else if (actionCode == EditorInfo.IME_ACTION_PREVIOUS) {
				View v = focusSearch(FOCUS_BACKWARD);
				if (v != null) {
					if (!v.requestFocus(FOCUS_BACKWARD)) {
						throw new IllegalStateException("focus search returned a view "
								+ "that wasn't able to take focus!");
					}
				}
				return;

			} else if (actionCode == EditorInfo.IME_ACTION_DONE) {
				InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
						Context.INPUT_METHOD_SERVICE);
				if (imm != null && imm.isActive(this)) {
					imm.hideSoftInputFromWindow(getWindowToken(), 0);
				}
				return;
			}
		}

		// ViewRootImpl viewRootImpl = getViewRootImpl();
		// if (viewRootImpl != null) {
		// long eventTime = SystemClock.uptimeMillis();
		// viewRootImpl.dispatchKeyFromIme(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN,
		// KeyEvent.KEYCODE_ENTER, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD
		// | KeyEvent.FLAG_KEEP_TOUCH_MODE | KeyEvent.FLAG_EDITOR_ACTION));
		// viewRootImpl.dispatchKeyFromIme(new KeyEvent(SystemClock.uptimeMillis(), eventTime, KeyEvent.ACTION_UP,
		// KeyEvent.KEYCODE_ENTER, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD
		// | KeyEvent.FLAG_KEEP_TOUCH_MODE | KeyEvent.FLAG_EDITOR_ACTION));
		// }
	}

	/**
	 * Set the extra input data of the text, which is the {@link EditorInfo#extras TextBoxAttribute.extras} Bundle that
	 * will be filled in when creating an input connection. The given integer is the resource ID of an XML resource
	 * holding an {@link android.R.styleable#InputExtras &lt;input-extras&gt;} XML tree.
	 * 
	 * @see #getInputExtras(boolean)
	 * @see EditorInfo#extras
	 * @attr ref android.R.styleable#TextView_editorExtras
	 */
	public void setInputExtras(int xmlResId) throws XmlPullParserException, IOException {
		XmlResourceParser parser = getResources().getXml(xmlResId);
		mEditor.mInputContentType.extras = new Bundle();
		getResources().parseBundleExtras(parser, mEditor.mInputContentType.extras);
	}

	/**
	 * Retrieve the input extras currently associated with the text view, which can be viewed as well as modified.
	 * 
	 * @param create
	 *            If true, the extras will be created if they don't already exist. Otherwise, null will be returned if
	 *            none have been created.
	 * @see #setInputExtras(int)
	 * @see EditorInfo#extras
	 * @attr ref android.R.styleable#TextView_editorExtras
	 */
	public Bundle getInputExtras(boolean create) {
		if (mEditor.mInputContentType == null) {
			if (!create)
				return null;
		}
		if (mEditor.mInputContentType.extras == null) {
			if (!create)
				return null;
			mEditor.mInputContentType.extras = new Bundle();
		}
		return mEditor.mInputContentType.extras;
	}

	// @Override
	// protected boolean setFrame(int l, int t, int r, int b) {
	// boolean result = super.setFrame(l, t, r, b);
	//
	// // mEditor.setFrame();
	//
	// return result;
	// }

	int getVerticalOffset(boolean forceNormal) {
		int voffset = 0;
		final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

		Layout l = mLayout;

		if (gravity != Gravity.TOP) {
			int boxht;

			if (l == null) {
				boxht = getMeasuredHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
			} else {
				boxht = getMeasuredHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom();
			}
			int textht = l.getHeight();

			if (textht < boxht) {
				if (gravity == Gravity.BOTTOM)
					voffset = boxht - textht;
				else
					// (gravity == Gravity.CENTER_VERTICAL)
					voffset = (boxht - textht) >> 1;
			}
		}
		return voffset;
	}

	private int getBottomVerticalOffset(boolean forceNormal) {
		int voffset = 0;
		final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

		Layout l = mLayout;

		if (gravity != Gravity.BOTTOM) {
			int boxht;

			if (l == null) {
				boxht = getMeasuredHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
			} else {
				boxht = getMeasuredHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom();
			}
			int textht = l.getHeight();

			if (textht < boxht) {
				if (gravity == Gravity.TOP)
					voffset = boxht - textht;
				else
					// (gravity == Gravity.CENTER_VERTICAL)
					voffset = (boxht - textht) >> 1;
			}
		}
		return voffset;
	}

	void invalidateCursorPath() {
		if (mHighlightPathBogus) {
			invalidateCursor();
		} else {
			final int horizontalPadding = getCompoundPaddingLeft();
			final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

			if (mEditor.mCursorCount == 0) {
				synchronized (TEMP_RECTF) {
					/*
					 * The reason for this concern about the thickness of the cursor and doing the floor/ceil on the
					 * coordinates is that some EditTexts (notably textfields in the Browser) have anti-aliased text
					 * where not all the characters are necessarily at integer-multiple locations. This should make sure
					 * the entire cursor gets invalidated instead of sometimes missing half a pixel.
					 */
					float thick = FloatMath.ceil(mTextPaint.getStrokeWidth());
					if (thick < 1.0f) {
						thick = 1.0f;
					}

					thick /= 2.0f;

					// mHighlightPath is guaranteed to be non null at that point.
					mHighlightPath.computeBounds(TEMP_RECTF, false);

					invalidate((int) FloatMath.floor(horizontalPadding + TEMP_RECTF.left - thick),
							(int) FloatMath.floor(verticalPadding + TEMP_RECTF.top - thick),
							(int) FloatMath.ceil(horizontalPadding + TEMP_RECTF.right + thick),
							(int) FloatMath.ceil(verticalPadding + TEMP_RECTF.bottom + thick));
				}
			} else {
				for (int i = 0; i < mEditor.mCursorCount; i++) {
					Rect bounds = mEditor.mCursorDrawable[i].getBounds();
					invalidate(bounds.left + horizontalPadding, bounds.top + verticalPadding, bounds.right
							+ horizontalPadding, bounds.bottom + verticalPadding);
				}
			}
		}
	}

	void invalidateCursor() {
		int where = getSelectionEnd();

		invalidateCursor(where, where, where);
	}

	private void invalidateCursor(int a, int b, int c) {
		if (a >= 0 || b >= 0 || c >= 0) {
			int start = Math.min(Math.min(a, b), c);
			int end = Math.max(Math.max(a, b), c);
			invalidateRegion(start, end, true /* Also invalidates blinking cursor */);
		}
	}

	/**
	 * Invalidates the region of text enclosed between the start and end text offsets.
	 */
	void invalidateRegion(int start, int end, boolean invalidateCursor) {
		if (mLayout == null) {
			invalidate();
		} else {
			int lineStart = mLayout.getLineForOffset(start);
			int top = mLayout.getLineTop(lineStart);

			// This is ridiculous, but the descent from the line above
			// can hang down into the line we really want to redraw,
			// so we have to invalidate part of the line above to make
			// sure everything that needs to be redrawn really is.
			// (But not the whole line above, because that would cause
			// the same problem with the descenders on the line above it!)
			if (lineStart > 0) {
				top -= mLayout.getLineDescent(lineStart - 1);
			}

			int lineEnd;

			if (start == end)
				lineEnd = lineStart;
			else
				lineEnd = mLayout.getLineForOffset(end);

			int bottom = mLayout.getLineBottom(lineEnd);

			// mEditor can be null in case selection is set programmatically.
			if (invalidateCursor && true) {
				for (int i = 0; i < mEditor.mCursorCount; i++) {
					Rect bounds = mEditor.mCursorDrawable[i].getBounds();
					top = Math.min(top, bounds.top);
					bottom = Math.max(bottom, bounds.bottom);
				}
			}

			final int compoundPaddingLeft = getCompoundPaddingLeft();
			final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

			int left, right;
			if (lineStart == lineEnd && !invalidateCursor) {
				left = (int) mLayout.getPrimaryHorizontal(start);
				right = (int) (mLayout.getPrimaryHorizontal(end) + 1.0);
				left += compoundPaddingLeft;
				right += compoundPaddingLeft;
			} else {
				// Rectangle bounding box when the region spans several lines
				left = compoundPaddingLeft;
				right = getWidth() - getCompoundPaddingRight();
			}

			invalidate(getScrollX() + left, verticalPadding + top, getScrollX() + right, verticalPadding + bottom);
		}
	}

	private void registerForPreDraw() {
		if (!mPreDrawRegistered) {
			getViewTreeObserver().addOnPreDrawListener(this);
			mPreDrawRegistered = true;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean onPreDraw() {
		if (mLayout == null) {
			assumeLayout();
		}

		boolean changed = false;

		if (mMovement != null) {
			/*
			 * This code also provides auto-scrolling when a cursor is moved using a CursorController (insertion point
			 * or selection limits). For selection, ensure start or end is visible depending on controller's state.
			 */
			int curs = getSelectionEnd();
			// Do not create the controller if it is not already created.
			// if (mEditor.mSelectionModifierCursorController != null
			// && mEditor.mSelectionModifierCursorController.isSelectionStartDragged()) {
			// curs = getSelectionStart();
			// }

			/*
			 * TODO: This should really only keep the end in view if it already was before the text changed. I'm not
			 * sure of a good way to tell from here if it was.
			 */
			if (curs < 0 && (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
				curs = mText.length();
			}

			if (curs >= 0) {
				changed = bringPointIntoView(curs);
			}
		} else {
			changed = bringTextIntoView();
		}

		getViewTreeObserver().removeOnPreDrawListener(this);
		mPreDrawRegistered = false;

		return !changed;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		mTemporaryDetach = false;

		mEditor.onAttachedToWindow();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		if (mPreDrawRegistered) {
			getViewTreeObserver().removeOnPreDrawListener(this);
			mPreDrawRegistered = false;
		}

		mEditor.onDetachedFromWindow();
	}

	@Override
	public void onScreenStateChanged(int screenState) {
		super.onScreenStateChanged(screenState);
		mEditor.onScreenStateChanged(screenState);
	}

	@Override
	protected boolean isPaddingOffsetRequired() {
		return mShadowRadius != 0 || false;
	}

	@Override
	protected int getLeftPaddingOffset() {
		return getCompoundPaddingLeft() - getPaddingLeft() + (int) Math.min(0, mShadowDx - mShadowRadius);
	}

	@Override
	protected int getTopPaddingOffset() {
		return (int) Math.min(0, mShadowDy - mShadowRadius);
	}

	@Override
	protected int getBottomPaddingOffset() {
		return (int) Math.max(0, mShadowDy + mShadowRadius);
	}

	@Override
	protected int getRightPaddingOffset() {
		return -(getCompoundPaddingRight() - getPaddingRight()) + (int) Math.max(0, mShadowDx + mShadowRadius);
	}

	@Override
	public boolean hasOverlappingRendering() {
		return (getBackground() != null || mText instanceof Spannable || hasSelection());
	}

	public boolean isTextSelectable() {
		return mEditor.mTextIsSelectable;
	}

	/**
	 * Sets whether or not (default) the content of this view is selectable by the user.
	 * 
	 * Note that this methods affect the {@link #setFocusable(boolean)}, {@link #setFocusableInTouchMode(boolean)}
	 * {@link #setClickable(boolean)} and {@link #setLongClickable(boolean)} states and you may want to restore these if
	 * they were customized.
	 * 
	 * See {@link #isTextSelectable} for details.
	 * 
	 * @param selectable
	 *            Whether or not the content of this TextView should be selectable.
	 */
	public void setTextIsSelectable(boolean selectable) {
		if (mEditor.mTextIsSelectable == selectable)
			return;

		mEditor.mTextIsSelectable = selectable;
		setFocusableInTouchMode(selectable);
		setFocusable(selectable);
		setClickable(selectable);
		setLongClickable(selectable);

		// mInputType should already be EditorInfo.TYPE_NULL and mInput should be null

		setMovementMethod(selectable ? ArrowKeyMovementMethod.getInstance() : null);
		setText(mText);

		// Called by setText above, but safer in case of future code changes
		mEditor.prepareCursorControllers();
	}

	private Path getUpdatedHighlightPath() {
		Path highlight = null;
		Paint highlightPaint = mHighlightPaint;

		final int selStart = getSelectionStart();
		final int selEnd = getSelectionEnd();
		if (mMovement != null && (isFocused() || isPressed()) && selStart >= 0) {
			if (selStart == selEnd) {
				if (mEditor.isCursorVisible()
						&& (SystemClock.uptimeMillis() - mEditor.mShowCursor) % (2 * Editor.BLINK) < Editor.BLINK) {
					if (mHighlightPathBogus) {
						if (mHighlightPath == null)
							mHighlightPath = new Path();
						mHighlightPath.reset();
						mLayout.getCursorPath(selStart, mHighlightPath, mText);
						mEditor.updateCursorsPositions();
						mHighlightPathBogus = false;
					}

					// XXX should pass to skin instead of drawing directly
					highlightPaint.setColor(mCurTextColor);
					highlightPaint.setStyle(Paint.Style.STROKE);
					highlight = mHighlightPath;
				}
			} else {
				if (mHighlightPathBogus) {
					if (mHighlightPath == null)
						mHighlightPath = new Path();
					mHighlightPath.reset();
					mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
					mHighlightPathBogus = false;
				}

				// XXX should pass to skin instead of drawing directly
				highlightPaint.setColor(mHighlightColor);
				highlightPaint.setStyle(Paint.Style.FILL);

				highlight = mHighlightPath;
			}
		}
		return highlight;
	}

	/**
	 * @hide
	 */
	public int getHorizontalOffsetForDrawables() {
		return 0;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// Draw the background for this view
		super.onDraw(canvas);

		final int compoundPaddingLeft = getCompoundPaddingLeft();
		final int compoundPaddingTop = getCompoundPaddingTop();
		final int compoundPaddingRight = getCompoundPaddingRight();
		final int compoundPaddingBottom = getCompoundPaddingBottom();
		final int scrollX = getScrollX();
		final int scrollY = getScrollY();
		final int right = getRight();
		final int left = getLeft();
		final int bottom = getBottom();
		final int top = getTop();

		int color = mCurTextColor;

		if (mLayout == null) {
			assumeLayout();
		}

		Layout layout = mLayout;

		mTextPaint.setColor(color);
		mTextPaint.drawableState = getDrawableState();

		canvas.save();

		int extendedPaddingTop = getExtendedPaddingTop();
		int extendedPaddingBottom = getExtendedPaddingBottom();

		final int vspace = getBottom() - getTop() - compoundPaddingBottom - compoundPaddingTop;
		final int maxScrollY = mLayout.getHeight() - vspace;

		float clipLeft = compoundPaddingLeft + scrollX;
		float clipTop = (scrollY == 0) ? 0 : extendedPaddingTop + scrollY;
		float clipRight = right - left - compoundPaddingRight + scrollX;
		float clipBottom = bottom - top + scrollY - ((scrollY == maxScrollY) ? 0 : extendedPaddingBottom);

		if (mShadowRadius != 0) {
			clipLeft += Math.min(0, mShadowDx - mShadowRadius);
			clipRight += Math.max(0, mShadowDx + mShadowRadius);

			clipTop += Math.min(0, mShadowDy - mShadowRadius);
			clipBottom += Math.max(0, mShadowDy + mShadowRadius);
		}

		canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);

		int voffsetText = 0;
		int voffsetCursor = 0;

		// translate in by our padding
		/* shortcircuit calling getVerticaOffset() */
		if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
			voffsetText = getVerticalOffset(false);
			voffsetCursor = getVerticalOffset(true);
		}
		canvas.translate(compoundPaddingLeft, extendedPaddingTop + voffsetText);

		final int cursorOffsetVertical = voffsetCursor - voffsetText;

		Path highlight = getUpdatedHighlightPath();
		mEditor.onDraw(canvas, layout, highlight, mHighlightPaint, cursorOffsetVertical);
		canvas.restore();
	}

	@Override
	public void getFocusedRect(Rect r) {
		if (mLayout == null) {
			super.getFocusedRect(r);
			return;
		}

		int selEnd = getSelectionEnd();
		if (selEnd < 0) {
			super.getFocusedRect(r);
			return;
		}

		int selStart = getSelectionStart();
		if (selStart < 0 || selStart >= selEnd) {
			int line = mLayout.getLineForOffset(selEnd);
			r.top = mLayout.getLineTop(line);
			r.bottom = mLayout.getLineBottom(line);
			r.left = (int) mLayout.getPrimaryHorizontal(selEnd) - 2;
			r.right = r.left + 4;
		} else {
			int lineStart = mLayout.getLineForOffset(selStart);
			int lineEnd = mLayout.getLineForOffset(selEnd);
			r.top = mLayout.getLineTop(lineStart);
			r.bottom = mLayout.getLineBottom(lineEnd);
			if (lineStart == lineEnd) {
				r.left = (int) mLayout.getPrimaryHorizontal(selStart);
				r.right = (int) mLayout.getPrimaryHorizontal(selEnd);
			} else {
				// Selection extends across multiple lines -- make the focused
				// rect cover the entire width.
				if (mHighlightPathBogus) {
					if (mHighlightPath == null)
						mHighlightPath = new Path();
					mHighlightPath.reset();
					mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
					mHighlightPathBogus = false;
				}
				synchronized (TEMP_RECTF) {
					mHighlightPath.computeBounds(TEMP_RECTF, true);
					r.left = (int) TEMP_RECTF.left - 1;
					r.right = (int) TEMP_RECTF.right + 1;
				}
			}
		}

		// Adjust for padding and gravity.
		int paddingLeft = getCompoundPaddingLeft();
		int paddingTop = getExtendedPaddingTop();
		if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
			paddingTop += getVerticalOffset(false);
		}
		r.offset(paddingLeft, paddingTop);
		int paddingBottom = getExtendedPaddingBottom();
		r.bottom += paddingBottom;
	}

	// /**
	// * Return the number of lines of text, or 0 if the internal Layout has not been built.
	// */
	// public int getLineCount() {
	// return mLayout != null ? mLayout.getLineCount() : 0;
	// }

	/**
	 * Return the baseline for the specified line (0...getLineCount() - 1) If bounds is not null, return the top, left,
	 * right, bottom extents of the specified line in it. If the internal Layout has not been built, return 0 and set
	 * bounds to (0, 0, 0, 0)
	 * 
	 * @param line
	 *            which line to examine (0..getLineCount() - 1)
	 * @param bounds
	 *            Optional. If not null, it returns the extent of the line
	 * @return the Y-coordinate of the baseline
	 */
	public int getLineBounds(int line, Rect bounds) {
		if (mLayout == null) {
			if (bounds != null) {
				bounds.set(0, 0, 0, 0);
			}
			return 0;
		} else {
			int baseline = mLayout.getLineBounds(line, bounds);

			int voffset = getExtendedPaddingTop();
			if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
				voffset += getVerticalOffset(true);
			}
			if (bounds != null) {
				bounds.offset(getCompoundPaddingLeft(), voffset);
			}
			return baseline + voffset;
		}
	}

	@Override
	public int getBaseline() {
		if (mLayout == null) {
			return super.getBaseline();
		}

		int voffset = 0;
		if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
			voffset = getVerticalOffset(true);
		}

		return getExtendedPaddingTop() + voffset + mLayout.getLineBaseline(0);
	}

	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			boolean isInSelectionMode = mEditor.mSelectionActionMode != null;

			if (isInSelectionMode) {
				if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
					KeyEvent.DispatcherState state = getKeyDispatcherState();
					if (state != null) {
						state.startTracking(event, this);
					}
					return true;
				} else if (event.getAction() == KeyEvent.ACTION_UP) {
					KeyEvent.DispatcherState state = getKeyDispatcherState();
					if (state != null) {
						state.handleUpEvent(event);
					}
					if (event.isTracking() && !event.isCanceled()) {
						return true;
					}
				}
			}
		}
		return super.onKeyPreIme(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		int which = doKeyDown(keyCode, event, null);
		if (which == 0) {
			// Go through default dispatching.
			return super.onKeyDown(keyCode, event);
		}

		return true;
	}

	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		KeyEvent down = KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN);

		int which = doKeyDown(keyCode, down, event);
		if (which == 0) {
			// Go through default dispatching.
			return super.onKeyMultiple(keyCode, repeatCount, event);
		}
		if (which == -1) {
			// Consumed the whole thing.
			return true;
		}

		repeatCount--;

		// We are going to dispatch the remaining events to either the input
		// or movement method. To do this, we will just send a repeated stream
		// of down and up events until we have done the complete repeatCount.
		// It would be nice if those interfaces had an onKeyMultiple() method,
		// but adding that is a more complicated change.
		KeyEvent up = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
		if (which == 1) {
			// mEditor and mEditor.mInput are not null from doKeyDown
			mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, up);
			while (--repeatCount > 0) {
				mEditor.mKeyListener.onKeyDown(this, (Editable) mText, keyCode, down);
				mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, up);
			}
		} else if (which == 2) {
			// mMovement is not null from doKeyDown
			// mMovement.onKeyUp(this, (Spannable) mText, keyCode, up);
			// while (--repeatCount > 0) {
			// mMovement.onKeyDown(this, (Spannable) mText, keyCode, down);
			// mMovement.onKeyUp(this, (Spannable) mText, keyCode, up);
			// }
		}

		return true;
	}

	/**
	 * Returns true if pressing ENTER in this field advances focus instead of inserting the character. This is true
	 * mostly in single-line fields, but also in mail addresses and subjects which will display on multiple lines but
	 * where it doesn't make sense to insert newlines.
	 */
	private boolean shouldAdvanceFocusOnEnter() {
		if (getKeyListener() == null) {
			return false;
		}

		if (mSingleLine) {
			return true;
		}

		if ((mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
			int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns true if pressing TAB in this field advances focus instead of inserting the character. Insert tabs only in
	 * multi-line editors.
	 */
	private boolean shouldAdvanceFocusOnTab() {
		if (getKeyListener() != null && !mSingleLine && true
				&& (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
			int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
			if (variation == EditorInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE
					|| variation == EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) {
				return false;
			}
		}
		return true;
	}

	private int doKeyDown(int keyCode, KeyEvent event, KeyEvent otherEvent) {
		if (!isEnabled()) {
			return 0;
		}

		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
			if (event.hasNoModifiers()) {
				// When mInputContentType is set, we know that we are
				// running in a "modern" cupcake environment, so don't need
				// to worry about the application trying to capture
				// enter key events.
				if (mEditor.mInputContentType != null) {
					// If there is an action listener, given them a
					// chance to consume the event.
					if (mEditor.mInputContentType.onEditorActionListener != null
							&& mEditor.mInputContentType.onEditorActionListener.onEditorAction(this,
									EditorInfo.IME_NULL, event)) {
						mEditor.mInputContentType.enterDown = true;
						// We are consuming the enter key for them.
						return -1;
					}
				}

				// If our editor should move focus when enter is pressed, or
				// this is a generated event from an IME action button, then
				// don't let it be inserted into the text.
				if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0 || shouldAdvanceFocusOnEnter()) {
					if (hasOnClickListeners()) {
						return 0;
					}
					return -1;
				}
			}
			break;

		case KeyEvent.KEYCODE_DPAD_CENTER:
			if (event.hasNoModifiers()) {
				if (shouldAdvanceFocusOnEnter()) {
					return 0;
				}
			}
			break;

		case KeyEvent.KEYCODE_TAB:
			if (event.hasNoModifiers() || event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
				if (shouldAdvanceFocusOnTab()) {
					return 0;
				}
			}
			break;

		// Has to be done on key down (and not on key up) to correctly be intercepted.
		case KeyEvent.KEYCODE_BACK:
			if (mEditor.mSelectionActionMode != null) {
				return -1;
			}
			break;
		}

		if (mEditor.mKeyListener != null) {
			boolean doDown = true;
			if (otherEvent != null) {
				try {
					beginBatchEdit();
					final boolean handled = mEditor.mKeyListener.onKeyOther(this, (Editable) mText, otherEvent);
					doDown = false;
					if (handled) {
						return -1;
					}
				} catch (AbstractMethodError e) {
					// onKeyOther was added after 1.0, so if it isn't
					// implemented we need to try to dispatch as a regular down.
				} finally {
					endBatchEdit();
				}
			}

			if (doDown) {
				beginBatchEdit();
				final boolean handled = mEditor.mKeyListener.onKeyDown(this, (Editable) mText, keyCode, event);
				endBatchEdit();
				if (handled)
					return 1;
			}
		}

		// bug 650865: sometimes we get a key event before a layout.
		// don't try to move around if we don't know the layout.

		if (mMovement != null && mLayout != null) {
			boolean doDown = true;
			if (otherEvent != null) {
				try {
					// boolean handled = mMovement.onKeyOther(this, (Spannable) mText, otherEvent);
					// doDown = false;
					// if (handled) {
					// return -1;
					// }
				} catch (AbstractMethodError e) {
					// onKeyOther was added after 1.0, so if it isn't
					// implemented we need to try to dispatch as a regular down.
				}
			}
			if (doDown) {
				// if (mMovement.onKeyDown(this, (Spannable) mText, keyCode, event))
				// return 2;
			}
		}

		return 0;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (!isEnabled()) {
			return super.onKeyUp(keyCode, event);
		}

		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
			if (event.hasNoModifiers()) {
				/*
				 * If there is a click listener, just call through to super, which will invoke it.
				 * 
				 * If there isn't a click listener, try to show the soft input method. (It will also call
				 * performClick(), but that won't do anything in this case.)
				 */
				if (!hasOnClickListeners()) {
					if (mMovement != null && mText instanceof Editable && mLayout != null && onCheckIsTextEditor()) {
						InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
								Context.INPUT_METHOD_SERVICE);
						viewClicked(imm);
						if (imm != null && getShowSoftInputOnFocus()) {
							imm.showSoftInput(this, 0);
						}
					}
				}
			}
			return super.onKeyUp(keyCode, event);

		case KeyEvent.KEYCODE_ENTER:
			if (event.hasNoModifiers()) {
				if (mEditor.mInputContentType != null && mEditor.mInputContentType.onEditorActionListener != null
						&& mEditor.mInputContentType.enterDown) {
					mEditor.mInputContentType.enterDown = false;
					if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(this, EditorInfo.IME_NULL,
							event)) {
						return true;
					}
				}

				if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0 || shouldAdvanceFocusOnEnter()) {
					/*
					 * If there is a click listener, just call through to super, which will invoke it.
					 * 
					 * If there isn't a click listener, try to advance focus, but still call through to super, which
					 * will reset the pressed state and longpress state. (It will also call performClick(), but that
					 * won't do anything in this case.)
					 */
					if (!hasOnClickListeners()) {
						View v = focusSearch(FOCUS_DOWN);

						if (v != null) {
							if (!v.requestFocus(FOCUS_DOWN)) {
								throw new IllegalStateException("focus search returned a view "
										+ "that wasn't able to take focus!");
							}

							/*
							 * Return true because we handled the key; super will return false because there was no
							 * click listener.
							 */
							super.onKeyUp(keyCode, event);
							return true;
						} else if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
							// No target for next focus, but make sure the IME
							// if this came from it.
							InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
									Context.INPUT_METHOD_SERVICE);
							if (imm != null && imm.isActive(this)) {
								imm.hideSoftInputFromWindow(getWindowToken(), 0);
							}
						}
					}
				}
				return super.onKeyUp(keyCode, event);
			}
			break;
		}

		if (mEditor.mKeyListener != null)
			if (mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event))
				return true;

		// if (mMovement != null && mLayout != null)
		// if (mMovement.onKeyUp(this, (Spannable) mText, keyCode, event))
		// return true;

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onCheckIsTextEditor() {
		// return mEditor.mInputType != EditorInfo.TYPE_NULL;
		return true;
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		if (onCheckIsTextEditor() && isEnabled()) {
			outAttrs.inputType = getInputType();
			if (mEditor.mInputContentType != null) {
				outAttrs.imeOptions = mEditor.mInputContentType.imeOptions;
				outAttrs.privateImeOptions = mEditor.mInputContentType.privateImeOptions;
				outAttrs.actionLabel = mEditor.mInputContentType.imeActionLabel;
				outAttrs.actionId = mEditor.mInputContentType.imeActionId;
				outAttrs.extras = mEditor.mInputContentType.extras;
			} else {
				outAttrs.imeOptions = EditorInfo.IME_NULL;
			}
			if (focusSearch(FOCUS_DOWN) != null) {
				outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_NEXT;
			}
			if (focusSearch(FOCUS_UP) != null) {
				outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
			}
			if ((outAttrs.imeOptions & EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_UNSPECIFIED) {
				if ((outAttrs.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
					// An action has not been set, but the enter key will move to
					// the next focus, so set the action to that.
					outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
				} else {
					// An action has not been set, and there is no focus to move
					// to, so let's just supply a "done" action.
					outAttrs.imeOptions |= EditorInfo.IME_ACTION_DONE;
				}
				if (!shouldAdvanceFocusOnEnter()) {
					outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
				}
			}
			// if (isMultilineInputType(outAttrs.inputType)) {
			// Multi-line text editors should always show an enter key.
			outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
			// }
			outAttrs.hintText = null;
			if (mText instanceof Editable) {
				InputConnection ic = new EditableInputConnection(this);
				outAttrs.initialSelStart = getSelectionStart();
				outAttrs.initialSelEnd = getSelectionEnd();
				outAttrs.initialCapsMode = ic.getCursorCapsMode(getInputType());
				return ic;
			}
		}
		return null;
	}

	/**
	 * If this TextView contains editable content, extract a portion of it based on the information in
	 * <var>request</var> in to <var>outText</var>.
	 * 
	 * @return Returns true if the text was successfully extracted, else false.
	 */
	public boolean extractText(ExtractedTextRequest request, ExtractedText outText) {
		return mEditor.extractText(request, outText);
	}

	/**
	 * This is used to remove all style-impacting spans from text before new extracted text is being replaced into it,
	 * so that we don't have any lingering spans applied during the replace.
	 */
	static void removeParcelableSpans(Spannable spannable, int start, int end) {
		Object[] spans = spannable.getSpans(start, end, ParcelableSpan.class);
		int i = spans.length;
		while (i > 0) {
			i--;
			spannable.removeSpan(spans[i]);
		}
	}

	/**
	 * Apply to this text view the given extracted text, as previously returned by
	 * {@link #extractText(ExtractedTextRequest, ExtractedText)}.
	 */
	public void setExtractedText(ExtractedText text) {
		Editable content = getEditableText();
		if (text.text != null) {
			if (content == null) {
				setText(text.text);
			} else if (text.partialStartOffset < 0) {
				removeParcelableSpans(content, 0, content.length());
				content.replace(0, content.length(), text.text);
			} else {
				final int N = content.length();
				int start = text.partialStartOffset;
				if (start > N)
					start = N;
				int end = text.partialEndOffset;
				if (end > N)
					end = N;
				removeParcelableSpans(content, start, end);
				content.replace(start, end, text.text);
			}
		}

		// Now set the selection position... make sure it is in range, to
		// avoid crashes. If this is a partial update, it is possible that
		// the underlying text may have changed, causing us problems here.
		// Also we just don't want to trust clients to do the right thing.
		Spannable sp = (Spannable) getText();
		final int N = sp.length();
		int start = text.selectionStart;
		if (start < 0)
			start = 0;
		else if (start > N)
			start = N;
		int end = text.selectionEnd;
		if (end < 0)
			end = 0;
		else if (end > N)
			end = N;
		Selection.setSelection(sp, start, end);

		// Finally, update the selection mode.
		// if ((text.flags & ExtractedText.FLAG_SELECTING) != 0) {
		// MetaKeyKeyListener.startSelecting(this, sp);
		// } else {
		// MetaKeyKeyListener.stopSelecting(this, sp);
		// }
	}

	/**
	 * @hide
	 */
	public void setExtracting(ExtractedTextRequest req) {
		if (mEditor.mInputMethodState != null) {
			mEditor.mInputMethodState.mExtractedTextRequest = req;
		}
		// This would stop a possible selection mode, but no such mode is started in case
		// extracted mode will start. Some text is selected though, and will trigger an action mode
		// in the extracted view.
		mEditor.hideControllers();
	}

	/**
	 * Called by the framework in response to a text completion from the current input method, provided by it calling
	 * {@link InputConnection#commitCompletion InputConnection.commitCompletion()}. The default implementation does
	 * nothing; text views that are supporting auto-completion should override this to do their desired behavior.
	 * 
	 * @param text
	 *            The auto complete text the user has selected.
	 */
	public void onCommitCompletion(CompletionInfo text) {
		// intentionally empty
	}

	// /**
	// * Called by the framework in response to a text auto-correction (such as fixing a typo using a a dictionnary)
	// from
	// * the current input method, provided by it calling {@link InputConnection#commitCorrection}
	// * InputConnection.commitCorrection()}. The default implementation flashes the background of the corrected word to
	// * provide feedback to the user.
	// *
	// * @param info
	// * The auto correct info about the text that was corrected.
	// */
	// public void onCommitCorrection(CorrectionInfo info) {
	// // mEditor.onCommitCorrection(info);
	// }

	public void beginBatchEdit() {
		mEditor.beginBatchEdit();
	}

	public void endBatchEdit() {
		mEditor.endBatchEdit();
	}

	/**
	 * Called by the framework in response to a request to begin a batch of edit operations through a call to link
	 * {@link #beginBatchEdit()}.
	 */
	public void onBeginBatchEdit() {
		// intentionally empty
	}

	/**
	 * Called by the framework in response to a request to end a batch of edit operations through a call to link
	 * {@link #endBatchEdit}.
	 */
	public void onEndBatchEdit() {
		// intentionally empty
	}

	/**
	 * Make a new Layout based on the already-measured size of the view, on the assumption that it was measured
	 * correctly at some point.
	 */
	private void assumeLayout() {
		int width = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();

		if (width < 1) {
			width = 0;
		}

		int physicalWidth = width;

		if (mHorizontallyScrolling) {
			width = VERY_WIDE;
		}

		makeNewLayout(width, physicalWidth, UNKNOWN_BORING, UNKNOWN_BORING, physicalWidth, false);
	}

	@Override
	public void onRtlPropertiesChanged(int layoutDirection) {
		if (mLayoutAlignment != null) {
			if (mResolvedTextAlignment == TEXT_ALIGNMENT_VIEW_START
					|| mResolvedTextAlignment == TEXT_ALIGNMENT_VIEW_END) {
				mLayoutAlignment = null;
			}
		}
	}

	private Layout.Alignment getLayoutAlignment() {
		if (mLayoutAlignment == null) {
			mResolvedTextAlignment = getTextAlignment();
			switch (mResolvedTextAlignment) {
			case TEXT_ALIGNMENT_GRAVITY:
				switch (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
				case Gravity.START:
					mLayoutAlignment = Layout.Alignment.ALIGN_NORMAL;
					break;
				case Gravity.END:
					mLayoutAlignment = Layout.Alignment.ALIGN_OPPOSITE;
					break;
				// case Gravity.LEFT:
				// mLayoutAlignment = Layout.Alignment.ALIGN_LEFT;
				// break;
				// case Gravity.RIGHT:
				// mLayoutAlignment = Layout.Alignment.ALIGN_RIGHT;
				// break;
				case Gravity.CENTER_HORIZONTAL:
					mLayoutAlignment = Layout.Alignment.ALIGN_CENTER;
					break;
				default:
					mLayoutAlignment = Layout.Alignment.ALIGN_NORMAL;
					break;
				}
				break;
			case TEXT_ALIGNMENT_TEXT_START:
				mLayoutAlignment = Layout.Alignment.ALIGN_NORMAL;
				break;
			case TEXT_ALIGNMENT_TEXT_END:
				mLayoutAlignment = Layout.Alignment.ALIGN_OPPOSITE;
				break;
			case TEXT_ALIGNMENT_CENTER:
				mLayoutAlignment = Layout.Alignment.ALIGN_CENTER;
				break;
			// case TEXT_ALIGNMENT_VIEW_START:
			// mLayoutAlignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Layout.Alignment.ALIGN_RIGHT
			// : Layout.Alignment.ALIGN_LEFT;
			// break;
			// case TEXT_ALIGNMENT_VIEW_END:
			// mLayoutAlignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? Layout.Alignment.ALIGN_LEFT
			// : Layout.Alignment.ALIGN_RIGHT;
			// break;
			case TEXT_ALIGNMENT_INHERIT:
				// This should never happen as we have already resolved the text alignment
				// but better safe than sorry so we just fall through
			default:
				mLayoutAlignment = Layout.Alignment.ALIGN_NORMAL;
				break;
			}
		}
		return mLayoutAlignment;
	}

	/**
	 * The width passed in is now the desired layout width, not the full view width with padding. {@hide}
	 */
	protected void makeNewLayout(int wantWidth, int hintWidth, BoringLayout.Metrics boring,
			BoringLayout.Metrics hintBoring, int ellipsisWidth, boolean bringIntoView) {
		// Update "old" cached values
		mOldMaximum = mMaximum;
		mOldMaxMode = mMaxMode;

		mHighlightPathBogus = true;

		if (wantWidth < 0) {
			wantWidth = 0;
		}
		if (hintWidth < 0) {
			hintWidth = 0;
		}

		Layout.Alignment alignment = getLayoutAlignment();
		mLayout = makeSingleLayout(wantWidth, boring, ellipsisWidth, alignment, false, true);

		if (bringIntoView) {
			registerForPreDraw();
		}

		// CursorControllers need a non-null mLayout
		mEditor.prepareCursorControllers();
	}

	private Layout makeSingleLayout(int wantWidth, BoringLayout.Metrics boring, int ellipsisWidth,
			Layout.Alignment alignment, boolean shouldEllipsize, boolean useSaved) {
		Layout result = null;
		// if (mText instanceof Spannable) {
		result = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth, alignment, mSpacingMult, mSpacingAdd,
				mIncludePad); // , getKeyListener() == null ? effectiveEllipsize : null, ellipsisWidth);
		// } else {
		// if (boring == UNKNOWN_BORING) {
		// boring = BoringLayout.isBoring(mTransformed, mTextPaint, mTextDir, mBoring);
		// if (boring != null) {
		// mBoring = boring;
		// }
		// }
		//
		// if (boring != null) {
		// if (boring.width <= wantWidth && (effectiveEllipsize == null || boring.width <= ellipsisWidth)) {
		// if (useSaved && mSavedLayout != null) {
		// result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint, wantWidth, alignment,
		// mSpacingMult, mSpacingAdd, boring, mIncludePad);
		// } else {
		// result = BoringLayout.make(mTransformed, mTextPaint, wantWidth, alignment, mSpacingMult,
		// mSpacingAdd, boring, mIncludePad);
		// }
		//
		// if (useSaved) {
		// mSavedLayout = (BoringLayout) result;
		// }
		// } else if (shouldEllipsize && boring.width <= wantWidth) {
		// if (useSaved && mSavedLayout != null) {
		// result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint, wantWidth, alignment,
		// mSpacingMult, mSpacingAdd, boring, mIncludePad, effectiveEllipsize, ellipsisWidth);
		// } else {
		// result = BoringLayout.make(mTransformed, mTextPaint, wantWidth, alignment, mSpacingMult,
		// mSpacingAdd, boring, mIncludePad, effectiveEllipsize, ellipsisWidth);
		// }
		// } else if (shouldEllipsize) {
		// result = new StaticLayout(mTransformed, 0, mTransformed.length(), mTextPaint, wantWidth, alignment,
		// mTextDir, mSpacingMult, mSpacingAdd, mIncludePad, effectiveEllipsize, ellipsisWidth,
		// mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
		// } else {
		// result = new StaticLayout(mTransformed, mTextPaint, wantWidth, alignment, mTextDir, mSpacingMult,
		// mSpacingAdd, mIncludePad);
		// }
		// } else if (shouldEllipsize) {
		// result = new StaticLayout(mTransformed, 0, mTransformed.length(), mTextPaint, wantWidth, alignment,
		// mTextDir, mSpacingMult, mSpacingAdd, mIncludePad, effectiveEllipsize, ellipsisWidth,
		// mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
		// } else {
		// result = new StaticLayout(mTransformed, mTextPaint, wantWidth, alignment, mTextDir, mSpacingMult,
		// mSpacingAdd, mIncludePad);
		// }
		// }
		return result;
	}

	private static int desired(Layout layout) {
		int n = layout.getLineCount();
		CharSequence text = layout.getText();
		float max = 0;

		// if any line was wrapped, we can't use it.
		// but it's ok for the last line not to have a newline

		for (int i = 0; i < n - 1; i++) {
			if (text.charAt(layout.getLineEnd(i) - 1) != '\n')
				return -1;
		}

		for (int i = 0; i < n; i++) {
			max = Math.max(max, layout.getLineWidth(i));
		}

		return (int) FloatMath.ceil(max);
	}

	/**
	 * Set whether the TextView includes extra top and bottom padding to make room for accents that go above the normal
	 * ascent and descent. The default is true.
	 * 
	 * @see #getIncludeFontPadding()
	 * 
	 * @attr ref android.R.styleable#TextView_includeFontPadding
	 */
	public void setIncludeFontPadding(boolean includepad) {
		if (mIncludePad != includepad) {
			mIncludePad = includepad;

			if (mLayout != null) {
				nullLayouts();
				requestLayout();
				invalidate();
			}
		}
	}

	private void nullLayouts() {
		if (mLayout instanceof BoringLayout && mSavedLayout == null) {
			mSavedLayout = (BoringLayout) mLayout;
		}
		if (null instanceof BoringLayout && mSavedHintLayout == null) {
			mSavedHintLayout = (BoringLayout) null;
		}
		// mSavedMarqueeModeLayout =
		mLayout = null;
		mBoring = null;
		// Since it depends on the value of mLayout
		mEditor.prepareCursorControllers();
	}

	/**
	 * Gets whether the TextView includes extra top and bottom padding to make room for accents that go above the normal
	 * ascent and descent.
	 * 
	 * @see #setIncludeFontPadding(boolean)
	 * 
	 * @attr ref android.R.styleable#TextView_includeFontPadding
	 */
	public boolean getIncludeFontPadding() {
		return mIncludePad;
	}

	private static final BoringLayout.Metrics UNKNOWN_BORING = new BoringLayout.Metrics();

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int width;
		int height;

		BoringLayout.Metrics boring = UNKNOWN_BORING;
		BoringLayout.Metrics hintBoring = UNKNOWN_BORING;

		// if (mTextDir == null) {
		// getTextDirectionHeuristic();
		// }

		int des = -1;
		boolean fromexisting = false;

		if (widthMode == MeasureSpec.EXACTLY) {
			// Parent has told us how big to be. So be it.
			width = widthSize;
		} else {
			if (mLayout != null && true) {
				des = desired(mLayout);
			}

			if (des < 0) {
				boring = BoringLayout.isBoring(mTransformed, mTextPaint, mBoring);
				if (boring != null) {
					mBoring = boring;
				}
			} else {
				fromexisting = true;
			}

			if (boring == null || boring == UNKNOWN_BORING) {
				if (des < 0) {
					des = (int) FloatMath.ceil(Layout.getDesiredWidth(mTransformed, mTextPaint));
				}
				width = des;
			} else {
				width = boring.width;
			}

			width += getCompoundPaddingLeft() + getCompoundPaddingRight();

			if (mMaxWidthMode == EMS) {
				width = Math.min(width, mMaxWidth * getLineHeight());
			} else {
				width = Math.min(width, mMaxWidth);
			}

			if (mMinWidthMode == EMS) {
				width = Math.max(width, mMinWidth * getLineHeight());
			} else {
				width = Math.max(width, mMinWidth);
			}

			// Check against our minimum width
			width = Math.max(width, getSuggestedMinimumWidth());

			if (widthMode == MeasureSpec.AT_MOST) {
				width = Math.min(widthSize, width);
			}
		}

		int want = width - getCompoundPaddingLeft() - getCompoundPaddingRight();
		int unpaddedWidth = want;

		if (mHorizontallyScrolling)
			want = VERY_WIDE;

		int hintWant = want;
		int hintWidth = hintWant;

		if (mLayout == null) {
			makeNewLayout(want, hintWant, boring, hintBoring, width - getCompoundPaddingLeft()
					- getCompoundPaddingRight(), false);
		} else {
			final boolean layoutChanged = (mLayout.getWidth() != want) || (hintWidth != hintWant)
					|| (mLayout.getEllipsizedWidth() != width - getCompoundPaddingLeft() - getCompoundPaddingRight());

			final boolean widthChanged = (true) && (true) && (want > mLayout.getWidth())
					&& (mLayout instanceof BoringLayout || (fromexisting && des >= 0 && des <= want));

			final boolean maximumChanged = (mMaxMode != mOldMaxMode) || (mMaximum != mOldMaximum);

			if (layoutChanged || maximumChanged) {
				if (!maximumChanged && widthChanged) {
					mLayout.increaseWidthTo(want);
				} else {
					makeNewLayout(want, hintWant, boring, hintBoring, width - getCompoundPaddingLeft()
							- getCompoundPaddingRight(), false);
				}
			} else {
				// Nothing has changed
			}
		}

		if (heightMode == MeasureSpec.EXACTLY) {
			// Parent has told us how big to be. So be it.
			height = heightSize;
			mDesiredHeightAtMeasure = -1;
		} else {
			int desired = getDesiredHeight();

			height = desired;
			mDesiredHeightAtMeasure = desired;

			if (heightMode == MeasureSpec.AT_MOST) {
				height = Math.min(desired, heightSize);
			}
		}

		int unpaddedHeight = height - getCompoundPaddingTop() - getCompoundPaddingBottom();
		if (mMaxMode == LINES && mLayout.getLineCount() > mMaximum) {
			unpaddedHeight = Math.min(unpaddedHeight, mLayout.getLineTop(mMaximum));
		}

		/*
		 * We didn't let makeNewLayout() register to bring the cursor into view, so do it here if there is any
		 * possibility that it is needed.
		 */
		if (mMovement != null || mLayout.getWidth() > unpaddedWidth || mLayout.getHeight() > unpaddedHeight) {
			registerForPreDraw();
		} else {
			scrollTo(0, 0);
		}

		setMeasuredDimension(width, height);
	}

	private int getDesiredHeight() {
		return Math.max(getDesiredHeight(mLayout, true), getDesiredHeight(null, false));
	}

	private int getDesiredHeight(Layout layout, boolean cap) {
		if (layout == null) {
			return 0;
		}

		int linecount = layout.getLineCount();
		int pad = getCompoundPaddingTop() + getCompoundPaddingBottom();
		int desired = layout.getLineTop(linecount);

		desired += pad;

		if (mMaxMode == LINES) {
			/*
			 * Don't cap the hint to a certain number of lines. (Do cap it, though, if we have a maximum pixel height.)
			 */
			if (cap) {
				if (linecount > mMaximum) {
					desired = layout.getLineTop(mMaximum);
					desired += pad;
					linecount = mMaximum;
				}
			}
		} else {
			desired = Math.min(desired, mMaximum);
		}

		if (mMinMode == LINES) {
			if (linecount < mMinimum) {
				desired += getLineHeight() * (mMinimum - linecount);
			}
		} else {
			desired = Math.max(desired, mMinimum);
		}

		// Check against our minimum height
		desired = Math.max(desired, getSuggestedMinimumHeight());

		return desired;
	}

	/**
	 * Check whether a change to the existing text layout requires a new view layout.
	 */
	private void checkForResize() {
		boolean sizeChanged = false;

		if (mLayout != null) {
			// Check if our width changed
			if (getLayoutParams().width == LayoutParams.WRAP_CONTENT) {
				sizeChanged = true;
				invalidate();
			}

			// Check if our height changed
			if (getLayoutParams().height == LayoutParams.WRAP_CONTENT) {
				int desiredHeight = getDesiredHeight();

				if (desiredHeight != this.getHeight()) {
					sizeChanged = true;
				}
			} else if (getLayoutParams().height == LayoutParams.MATCH_PARENT) {
				if (mDesiredHeightAtMeasure >= 0) {
					int desiredHeight = getDesiredHeight();

					if (desiredHeight != mDesiredHeightAtMeasure) {
						sizeChanged = true;
					}
				}
			}
		}

		if (sizeChanged) {
			requestLayout();
			// caller will have already invalidated
		}
	}

	/**
	 * Check whether entirely new text requires a new view layout or merely a new text layout.
	 */
	private void checkForRelayout() {
		// If we have a fixed width, we can just swap in a new text layout
		// if the text height stays the same or if the view height is fixed.

		if ((getLayoutParams().width != LayoutParams.WRAP_CONTENT || (mMaxWidthMode == mMinWidthMode && mMaxWidth == mMinWidth))
				&& (getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight() > 0)) {
			// Static width, so try making a new text layout.

			// int oldht = mLayout.getHeight();
			int want = mLayout.getWidth();
			int hintWant = 0;

			/*
			 * No need to bring the text into view, since the size is not changing (unless we do the requestLayout(), in
			 * which case it will happen at measure).
			 */
			makeNewLayout(want, hintWant, UNKNOWN_BORING, UNKNOWN_BORING, getRight() - getLeft()
					- getCompoundPaddingLeft() - getCompoundPaddingRight(), false);

			// We lose: the height has changed and we have a dynamic height.
			// Request a new view layout using our new text layout.
			requestLayout();
			invalidate();
		} else {
			// Dynamic width, so we have no choice but to request a new
			// view layout with a new text layout.
			nullLayouts();
			requestLayout();
			invalidate();
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (mDeferScroll >= 0) {
			int curs = mDeferScroll;
			mDeferScroll = -1;
			bringPointIntoView(Math.min(curs, mText.length()));
		}
		// if (changed && true)
		// mEditor.invalidateTextDisplayList();
	}

	/**
	 * Returns true if anything changed.
	 */
	private boolean bringTextIntoView() {
		Layout layout = mLayout;
		int line = 0;
		if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
			line = layout.getLineCount() - 1;
		}

		Layout.Alignment a = layout.getParagraphAlignment(line);
		int dir = layout.getParagraphDirection(line);
		int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
		int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
		int ht = layout.getHeight();

		int scrollx = 0, scrolly = 0;

		// Convert to left, center, or right alignment.
		// if (a == Layout.Alignment.ALIGN_NORMAL) {
		// a = dir == Layout.DIR_LEFT_TO_RIGHT ? Layout.Alignment.ALIGN_LEFT : Layout.Alignment.ALIGN_RIGHT;
		// } else if (a == Layout.Alignment.ALIGN_OPPOSITE) {
		// a = dir == Layout.DIR_LEFT_TO_RIGHT ? Layout.Alignment.ALIGN_RIGHT : Layout.Alignment.ALIGN_LEFT;
		// }

		if (a == Layout.Alignment.ALIGN_CENTER) {
			/*
			 * Keep centered if possible, or, if it is too wide to fit, keep leading edge in view.
			 */

			int left = (int) FloatMath.floor(layout.getLineLeft(line));
			int right = (int) FloatMath.ceil(layout.getLineRight(line));

			if (right - left < hspace) {
				scrollx = (right + left) / 2 - hspace / 2;
			} else {
				if (dir < 0) {
					scrollx = right - hspace;
				} else {
					scrollx = left;
				}
			}
		}
		// else if (a == Layout.Alignment.ALIGN_RIGHT) {
		// int right = (int) FloatMath.ceil(layout.getLineRight(line));
		// scrollx = right - hspace;
		// } else { // a == Layout.Alignment.ALIGN_LEFT (will also be the default)
		// scrollx = (int) FloatMath.floor(layout.getLineLeft(line));
		// }

		if (ht < vspace) {
			scrolly = 0;
		} else {
			if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
				scrolly = ht - vspace;
			} else {
				scrolly = 0;
			}
		}

		if (scrollx != getScrollX() || scrolly != getScrollY()) {
			scrollTo(scrollx, scrolly);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Move the point, specified by the offset, into the view if it is needed. This has to be called after layout.
	 * Returns true if anything changed.
	 */
	public boolean bringPointIntoView(int offset) {
		if (isLayoutRequested()) {
			mDeferScroll = offset;
			return false;
		}
		boolean changed = false;

		Layout layout = mLayout;

		if (layout == null)
			return changed;

		int line = layout.getLineForOffset(offset);

		// FIXME: Is it okay to truncate this, or should we round?
		final int x = (int) layout.getPrimaryHorizontal(offset);
		final int top = layout.getLineTop(line);
		final int bottom = layout.getLineTop(line + 1);

		int left = (int) FloatMath.floor(layout.getLineLeft(line));
		int right = (int) FloatMath.ceil(layout.getLineRight(line));
		int ht = layout.getHeight();

		int grav;

		switch (layout.getParagraphAlignment(line)) {
		// case ALIGN_LEFT:
		// grav = 1;
		// break;
		// case ALIGN_RIGHT:
		// grav = -1;
		// break;
		case ALIGN_NORMAL:
			grav = layout.getParagraphDirection(line);
			break;
		case ALIGN_OPPOSITE:
			grav = -layout.getParagraphDirection(line);
			break;
		case ALIGN_CENTER:
		default:
			grav = 0;
			break;
		}

		int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
		int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();

		int hslack = (bottom - top) / 2;
		int vslack = hslack;

		if (vslack > vspace / 4)
			vslack = vspace / 4;
		if (hslack > hspace / 4)
			hslack = hspace / 4;

		int hs = getScrollX();
		int vs = getScrollY();

		if (top - vs < vslack)
			vs = top - vslack;
		if (bottom - vs > vspace - vslack)
			vs = bottom - (vspace - vslack);
		if (ht - vs < vspace)
			vs = ht - vspace;
		if (0 - vs > 0)
			vs = 0;

		if (grav != 0) {
			if (x - hs < hslack) {
				hs = x - hslack;
			}
			if (x - hs > hspace - hslack) {
				hs = x - (hspace - hslack);
			}
		}

		if (grav < 0) {
			if (left - hs > 0)
				hs = left;
			if (right - hs < hspace)
				hs = right - hspace;
		} else if (grav > 0) {
			if (right - hs < hspace)
				hs = right - hspace;
			if (left - hs > 0)
				hs = left;
		} else /* grav == 0 */{
			if (right - left <= hspace) {
				/*
				 * If the entire text fits, center it exactly.
				 */
				hs = left - (hspace - (right - left)) / 2;
			} else if (x > right - hslack) {
				/*
				 * If we are near the right edge, keep the right edge at the edge of the view.
				 */
				hs = right - hspace;
			} else if (x < left + hslack) {
				/*
				 * If we are near the left edge, keep the left edge at the edge of the view.
				 */
				hs = left;
			} else if (left > hs) {
				/*
				 * Is there whitespace visible at the left? Fix it if so.
				 */
				hs = left;
			} else if (right < hs + hspace) {
				/*
				 * Is there whitespace visible at the right? Fix it if so.
				 */
				hs = right - hspace;
			} else {
				/*
				 * Otherwise, float as needed.
				 */
				if (x - hs < hslack) {
					hs = x - hslack;
				}
				if (x - hs > hspace - hslack) {
					hs = x - (hspace - hslack);
				}
			}
		}

		if (hs != getScrollX() || vs != getScrollY()) {
			if (mScroller == null) {
				scrollTo(hs, vs);
			} else {
				long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
				int dx = hs - getScrollX();
				int dy = vs - getScrollY();

				if (duration > ANIMATED_SCROLL_GAP) {
					mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
					awakenScrollBars(mScroller.getDuration());
					invalidate();
				} else {
					if (!mScroller.isFinished()) {
						mScroller.abortAnimation();
					}

					scrollBy(dx, dy);
				}

				mLastScroll = AnimationUtils.currentAnimationTimeMillis();
			}

			changed = true;
		}

		if (isFocused()) {
			// This offsets because getInterestingRect() is in terms of viewport coordinates, but
			// requestRectangleOnScreen() is in terms of content coordinates.

			// The offsets here are to ensure the rectangle we are using is
			// within our view bounds, in case the cursor is on the far left
			// or right. If it isn't withing the bounds, then this request
			// will be ignored.
			if (mTempRect == null)
				mTempRect = new Rect();
			mTempRect.set(x - 2, top, x + 2, bottom);
			getInterestingRect(mTempRect, line);
			mTempRect.offset(getScrollX(), getScrollY());

			if (requestRectangleOnScreen(mTempRect)) {
				changed = true;
			}
		}

		return changed;
	}

	/**
	 * Move the cursor, if needed, so that it is at an offset that is visible to the user. This will not move the cursor
	 * if it represents more than one character (a selection range). This will only work if the TextView contains
	 * spannable text; otherwise it will do nothing.
	 * 
	 * @return True if the cursor was actually moved, false otherwise.
	 */
	public boolean moveCursorToVisibleOffset() {
		if (!(mText instanceof Spannable)) {
			return false;
		}
		int start = getSelectionStart();
		int end = getSelectionEnd();
		if (start != end) {
			return false;
		}

		// First: make sure the line is visible on screen:

		int line = mLayout.getLineForOffset(start);

		final int top = mLayout.getLineTop(line);
		final int bottom = mLayout.getLineTop(line + 1);
		final int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
		int vslack = (bottom - top) / 2;
		if (vslack > vspace / 4)
			vslack = vspace / 4;
		final int vs = getScrollY();

		if (top < (vs + vslack)) {
			line = mLayout.getLineForVertical(vs + vslack + (bottom - top));
		} else if (bottom > (vspace + vs - vslack)) {
			line = mLayout.getLineForVertical(vspace + vs - vslack - (bottom - top));
		}

		// Next: make sure the character is visible on screen:

		final int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
		final int hs = getScrollX();
		final int leftChar = mLayout.getOffsetForHorizontal(line, hs);
		final int rightChar = mLayout.getOffsetForHorizontal(line, hspace + hs);

		// line might contain bidirectional text
		final int lowChar = leftChar < rightChar ? leftChar : rightChar;
		final int highChar = leftChar > rightChar ? leftChar : rightChar;

		int newStart = start;
		if (newStart < lowChar) {
			newStart = lowChar;
		} else if (newStart > highChar) {
			newStart = highChar;
		}

		if (newStart != start) {
			Selection.setSelection((Spannable) mText, newStart);
			return true;
		}

		return false;
	}

	@Override
	public void computeScroll() {
		if (mScroller != null) {
			if (mScroller.computeScrollOffset()) {
				// getScrollX() = mScroller.getCurrX();
				// getScrollY() = mScroller.getCurrY();
				// invalidateParentCaches();
				postInvalidate(); // So we draw again
			}
		}
	}

	private void getInterestingRect(Rect r, int line) {
		convertFromViewportToContentCoordinates(r);

		// Rectangle can can be expanded on first and last line to take
		// padding into account.
		// TODO Take left/right padding into account too?
		if (line == 0)
			r.top -= getExtendedPaddingTop();
		if (line == mLayout.getLineCount() - 1)
			r.bottom += getExtendedPaddingBottom();
	}

	private void convertFromViewportToContentCoordinates(Rect r) {
		final int horizontalOffset = viewportToContentHorizontalOffset();
		r.left += horizontalOffset;
		r.right += horizontalOffset;

		final int verticalOffset = viewportToContentVerticalOffset();
		r.top += verticalOffset;
		r.bottom += verticalOffset;
	}

	int viewportToContentHorizontalOffset() {
		return getCompoundPaddingLeft() - getScrollX();
	}

	int viewportToContentVerticalOffset() {
		int offset = getExtendedPaddingTop() - getScrollY();
		if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
			offset += getVerticalOffset(false);
		}
		return offset;
	}

	// @Override
	// public void debug(int depth) {
	// super.debug(depth);
	//
	// String output = debugIndent(depth);
	// output += "frame={" + getLeft() + ", " + getTop() + ", " + getRight() + ", " + getBottom() + "} scroll={"
	// + getScrollX() + ", " + getScrollY() + "} ";
	//
	// if (mText != null) {
	//
	// output += "mText=\"" + mText + "\" ";
	// if (mLayout != null) {
	// output += "mLayout width=" + mLayout.getWidth() + " height=" + mLayout.getHeight();
	// }
	// } else {
	// output += "mText=NULL";
	// }
	// Log.d(VIEW_LOG_TAG, output);
	// }

	/**
	 * Convenience for {@link Selection#getSelectionStart}.
	 */
	@ViewDebug.ExportedProperty(category = "text")
	public int getSelectionStart() {
		return Selection.getSelectionStart(getText());
	}

	/**
	 * Convenience for {@link Selection#getSelectionEnd}.
	 */
	@ViewDebug.ExportedProperty(category = "text")
	public int getSelectionEnd() {
		return Selection.getSelectionEnd(getText());
	}

	/**
	 * Return true iff there is a selection inside this text view.
	 */
	public boolean hasSelection() {
		final int selectionStart = getSelectionStart();
		final int selectionEnd = getSelectionEnd();

		return selectionStart >= 0 && selectionStart != selectionEnd;
	}

	/**
	 * Set the TextView so that when it takes focus, all the text is selected.
	 * 
	 * @attr ref android.R.styleable#TextView_selectAllOnFocus
	 */
	public void setSelectAllOnFocus(boolean selectAllOnFocus) {
		mEditor.mSelectAllOnFocus = selectAllOnFocus;

		if (selectAllOnFocus && !(mText instanceof Spannable)) {
			setText(mText);
		}
	}

	/**
	 * Set whether the cursor is visible. The default is true. Note that this property only makes sense for editable
	 * TextView.
	 * 
	 * @see #isCursorVisible()
	 * 
	 * @attr ref android.R.styleable#TextView_cursorVisible
	 */
	public void setCursorVisible(boolean visible) {
		if (mEditor.mCursorVisible != visible) {
			mEditor.mCursorVisible = visible;
			invalidate();

			mEditor.makeBlink();

			// InsertionPointCursorController depends on mCursorVisible
			mEditor.prepareCursorControllers();
		}
	}

	/**
	 * @return whether or not the cursor is visible (assuming this TextView is editable)
	 * 
	 * @see #setCursorVisible(boolean)
	 * 
	 * @attr ref android.R.styleable#TextView_cursorVisible
	 */
	public boolean isCursorVisible() {
		// true is the default value
		return mEditor.mCursorVisible;
	}

	/**
	 * This method is called when the text is changed, in case any subclasses would like to know.
	 * 
	 * Within <code>text</code>, the <code>lengthAfter</code> characters beginning at <code>start</code> have just
	 * replaced old text that had length <code>lengthBefore</code>. It is an error to attempt to make changes to
	 * <code>text</code> from this callback.
	 * 
	 * @param text
	 *            The text the TextView is displaying
	 * @param start
	 *            The offset of the start of the range of the text that was modified
	 * @param lengthBefore
	 *            The length of the former text that has been replaced
	 * @param lengthAfter
	 *            The length of the replacement modified text
	 */
	protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
		// intentionally empty, template pattern method can be overridden by subclasses
	}

	/**
	 * This method is called when the selection has changed, in case any subclasses would like to know.
	 * 
	 * @param selStart
	 *            The new selection start location.
	 * @param selEnd
	 *            The new selection end location.
	 */
	protected void onSelectionChanged(int selStart, int selEnd) {
		sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
	}

	/**
	 * Adds a TextWatcher to the list of those whose methods are called whenever this TextView's text changes.
	 * <p>
	 * In 1.0, the {@link TextWatcher#afterTextChanged} method was erroneously not called after {@link #setText} calls.
	 * Now, doing {@link #setText} if there are any text changed listeners forces the buffer type to Editable if it
	 * would not otherwise be and does call this method.
	 */
	public void addTextChangedListener(TextWatcher watcher) {
		if (mListeners == null) {
			mListeners = new ArrayList<TextWatcher>();
		}

		mListeners.add(watcher);
	}

	/**
	 * Removes the specified TextWatcher from the list of those whose methods are called whenever this TextView's text
	 * changes.
	 */
	public void removeTextChangedListener(TextWatcher watcher) {
		if (mListeners != null) {
			int i = mListeners.indexOf(watcher);

			if (i >= 0) {
				mListeners.remove(i);
			}
		}
	}

	private void sendBeforeTextChanged(CharSequence text, int start, int before, int after) {
		if (mListeners != null) {
			final ArrayList<TextWatcher> list = mListeners;
			final int count = list.size();
			for (int i = 0; i < count; i++) {
				list.get(i).beforeTextChanged(text, start, before, after);
			}
		}

		// The spans that are inside or intersect the modified region no longer make sense
		// removeIntersectingSpans(start, start + before, SpellCheckSpan.class);
		// removeIntersectingSpans(start, start + before, SuggestionSpan.class);
	}

	// Removes all spans that are inside or actually overlap the start..end range
	@SuppressWarnings("unused")
	private <T> void removeIntersectingSpans(int start, int end, Class<T> type) {
		if (!(mText instanceof Editable))
			return;
		Editable text = (Editable) mText;

		T[] spans = text.getSpans(start, end, type);
		final int length = spans.length;
		for (int i = 0; i < length; i++) {
			final int s = text.getSpanStart(spans[i]);
			final int e = text.getSpanEnd(spans[i]);
			// Spans that are adjacent to the edited region will be handled in
			// updateSpellCheckSpans. Result depends on what will be added (space or text)
			if (e == start || s == end)
				break;
			text.removeSpan(spans[i]);
		}
	}

	/**
	 * Not private so it can be called from an inner class without going through a thunk.
	 */
	void sendOnTextChanged(CharSequence text, int start, int before, int after) {
		if (mListeners != null) {
			final ArrayList<TextWatcher> list = mListeners;
			final int count = list.size();
			for (int i = 0; i < count; i++) {
				list.get(i).onTextChanged(text, start, before, after);
			}
		}

		mEditor.sendOnTextChanged(start, after);
	}

	/**
	 * Not private so it can be called from an inner class without going through a thunk.
	 */
	void sendAfterTextChanged(Editable text) {
		if (mListeners != null) {
			final ArrayList<TextWatcher> list = mListeners;
			final int count = list.size();
			for (int i = 0; i < count; i++) {
				list.get(i).afterTextChanged(text);
			}
		}
	}

	void updateAfterEdit() {
		invalidate();
		int curs = getSelectionStart();

		if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
			registerForPreDraw();
		}

		checkForResize();

		if (curs >= 0) {
			mHighlightPathBogus = true;
			mEditor.makeBlink();
			bringPointIntoView(curs);
		}
	}

	/**
	 * Not private so it can be called from an inner class without going through a thunk.
	 */
	void handleTextChanged(CharSequence buffer, int start, int before, int after) {
		final Editor.InputMethodState ims = mEditor.mInputMethodState;
		if (ims == null || ims.mBatchEditNesting == 0) {
			updateAfterEdit();
		}
		if (ims != null) {
			ims.mContentChanged = true;
			if (ims.mChangedStart < 0) {
				ims.mChangedStart = start;
				ims.mChangedEnd = start + before;
			} else {
				ims.mChangedStart = Math.min(ims.mChangedStart, start);
				ims.mChangedEnd = Math.max(ims.mChangedEnd, start + before - ims.mChangedDelta);
			}
			ims.mChangedDelta += after - before;
		}

		sendOnTextChanged(buffer, start, before, after);
		onTextChanged(buffer, start, before, after);
	}

	/**
	 * Not private so it can be called from an inner class without going through a thunk.
	 */
	void spanChange(Spanned buf, Object what, int oldStart, int newStart, int oldEnd, int newEnd) {
		// XXX Make the start and end move together if this ends up
		// spending too much time invalidating.

		boolean selChanged = false;
		int newSelStart = -1, newSelEnd = -1;

		final Editor.InputMethodState ims = mEditor.mInputMethodState;

		if (what == Selection.SELECTION_END) {
			selChanged = true;
			newSelEnd = newStart;

			if (oldStart >= 0 || newStart >= 0) {
				invalidateCursor(Selection.getSelectionStart(buf), oldStart, newStart);
				checkForResize();
				registerForPreDraw();
				mEditor.makeBlink();
			}
		}

		if (what == Selection.SELECTION_START) {
			selChanged = true;
			newSelStart = newStart;

			if (oldStart >= 0 || newStart >= 0) {
				int end = Selection.getSelectionEnd(buf);
				invalidateCursor(end, oldStart, newStart);
			}
		}

		if (selChanged) {
			mHighlightPathBogus = true;
			if (!isFocused())
				mEditor.mSelectionMoved = true;

			if ((buf.getSpanFlags(what) & Spanned.SPAN_INTERMEDIATE) == 0) {
				if (newSelStart < 0) {
					newSelStart = Selection.getSelectionStart(buf);
				}
				if (newSelEnd < 0) {
					newSelEnd = Selection.getSelectionEnd(buf);
				}
				onSelectionChanged(newSelStart, newSelEnd);
			}
		}

		if (what instanceof UpdateAppearance || what instanceof ParagraphStyle || what instanceof CharacterStyle) {
			if (ims == null || ims.mBatchEditNesting == 0) {
				invalidate();
				mHighlightPathBogus = true;
				checkForResize();
			} else {
				ims.mContentChanged = true;
			}
		}

		if (MetaKeyKeyListener.isMetaTracker(buf, what)) {
			mHighlightPathBogus = true;
			if (ims != null && MetaKeyKeyListener.isSelectingMetaTracker(buf, what)) {
				ims.mSelectionModeChanged = true;
			}

			if (Selection.getSelectionStart(buf) >= 0) {
				if (ims == null || ims.mBatchEditNesting == 0) {
					invalidateCursor();
				} else {
					ims.mCursorChanged = true;
				}
			}
		}

		if (what instanceof ParcelableSpan) {
			// If this is a span that can be sent to a remote process,
			// the current extract editor would be interested in it.
			if (ims != null && ims.mExtractedTextRequest != null) {
				if (ims.mBatchEditNesting != 0) {
					if (oldStart >= 0) {
						if (ims.mChangedStart > oldStart) {
							ims.mChangedStart = oldStart;
						}
						if (ims.mChangedStart > oldEnd) {
							ims.mChangedStart = oldEnd;
						}
					}
					if (newStart >= 0) {
						if (ims.mChangedStart > newStart) {
							ims.mChangedStart = newStart;
						}
						if (ims.mChangedStart > newEnd) {
							ims.mChangedStart = newEnd;
						}
					}
				} else {
					if (DEBUG_EXTRACT)
						Log.v(LOG_TAG, "Span change outside of batch: " + oldStart + "-" + oldEnd + "," + newStart
								+ "-" + newEnd + " " + what);
					ims.mContentChanged = true;
				}
			}
		}
	}

	@Override
	public void onStartTemporaryDetach() {
		super.onStartTemporaryDetach();
		// Only track when onStartTemporaryDetach() is called directly,
		// usually because this instance is an editable field in a list
		if (!mDispatchTemporaryDetach)
			mTemporaryDetach = true;

		// Tell the editor that we are temporarily detached. It can use this to preserve
		// selection state as needed.
		mEditor.mTemporaryDetach = true;
	}

	@Override
	public void onFinishTemporaryDetach() {
		super.onFinishTemporaryDetach();
		// Only track when onStartTemporaryDetach() is called directly,
		// usually because this instance is an editable field in a list
		if (!mDispatchTemporaryDetach)
			mTemporaryDetach = false;
		mEditor.mTemporaryDetach = false;
	}

	@Override
	protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
		if (mTemporaryDetach) {
			// If we are temporarily in the detach state, then do nothing.
			super.onFocusChanged(focused, direction, previouslyFocusedRect);
			return;
		}

		mEditor.onFocusChanged(focused, direction);

		if (focused) {
			if (mText instanceof Spannable) {
				Spannable sp = (Spannable) mText;
				MetaKeyKeyListener.resetMetaState(sp);
			}
		}

		// if (mTransformation != null) {
		// mTransformation.onFocusChanged(this, mText, focused, direction, previouslyFocusedRect);
		// }

		super.onFocusChanged(focused, direction, previouslyFocusedRect);
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);

		mEditor.onWindowFocusChanged(hasWindowFocus);
	}

	@Override
	protected void onVisibilityChanged(View changedView, int visibility) {
		super.onVisibilityChanged(changedView, visibility);
		if (visibility != VISIBLE) {
			mEditor.hideControllers();
		}
	}

	/**
	 * Use {@link BaseInputConnection#removeComposingSpans BaseInputConnection.removeComposingSpans()} to remove any IME
	 * composing state from this text view.
	 */
	public void clearComposingText() {
		if (mText instanceof Spannable) {
			BaseInputConnection.removeComposingSpans((Spannable) mText);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		final int action = event.getActionMasked();

		mEditor.onTouchEvent(event);

		final boolean superResult = super.onTouchEvent(event);

		/*
		 * Don't handle the release after a long press, because it will move the selection away from whatever the menu
		 * action was trying to affect.
		 */
		if (mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
			mEditor.mDiscardNextActionUp = false;
			return superResult;
		}

		final boolean touchIsFinished = (action == MotionEvent.ACTION_UP) && (false || !mEditor.mIgnoreActionUpEvent)
				&& isFocused();

		if ((mMovement != null || onCheckIsTextEditor()) && isEnabled() && mText instanceof Spannable
				&& mLayout != null) {
			boolean handled = false;

			// if (mMovement != null) {
			// handled |= mMovement.onTouchEvent(this, (Spannable) mText, event);
			// }

			final boolean textIsSelectable = isTextSelectable();
			// if (touchIsFinished && mLinksClickable && mAutoLinkMask != 0 && textIsSelectable) {
			// // The LinkMovementMethod which should handle taps on links has not been installed
			// // on non editable text that support text selection.
			// // We reproduce its behavior here to open links for these.
			// ClickableSpan[] links = ((Spannable) mText).getSpans(getSelectionStart(), getSelectionEnd(),
			// ClickableSpan.class);
			//
			// if (links.length > 0) {
			// links[0].onClick(this);
			// handled = true;
			// }
			// }

			if (touchIsFinished && (isTextEditable() || textIsSelectable)) {
				// Show the IME, except when selecting in read-only text.
				final InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
						Context.INPUT_METHOD_SERVICE);
				viewClicked(imm);
				if (!textIsSelectable && mEditor.mShowSoftInputOnFocus) {
					handled |= imm != null && imm.showSoftInput(this, 0);
				}

				// The above condition ensures that the mEditor is not null
				mEditor.onTouchUpEvent(event);

				handled = true;
			}

			if (handled) {
				return true;
			}
		}

		return superResult;
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (mMovement != null && mText instanceof Spannable && mLayout != null) {
			try {
				// if (mMovement.onGenericMotionEvent(this, (Spannable) mText, event)) {
				// return true;
				// }
			} catch (AbstractMethodError ex) {
				// onGenericMotionEvent was added to the MovementMethod interface in API 12.
				// Ignore its absence in case third party applications implemented the
				// interface directly.
			}
		}
		return super.onGenericMotionEvent(event);
	}

	/**
	 * @return True iff this TextView contains a text that can be edited, or if this is a selectable TextView.
	 */
	boolean isTextEditable() {
		return mText instanceof Editable && onCheckIsTextEditor() && isEnabled();
	}

	/**
	 * Returns true, only while processing a touch gesture, if the initial touch down event caused focus to move to the
	 * text view and as a result its selection changed. Only valid while processing the touch gesture of interest, in an
	 * editable text view.
	 */
	public boolean didTouchFocusSelect() {
		return mEditor.mTouchFocusSelected;
	}

	@Override
	public void cancelLongPress() {
		super.cancelLongPress();
		mEditor.mIgnoreActionUpEvent = true;
	}

	public void setScroller(Scroller s) {
		mScroller = s;
	}

	@Override
	public boolean onKeyShortcut(int keyCode, KeyEvent event) {
		final int filteredMetaState = event.getMetaState() & ~KeyEvent.META_CTRL_MASK;
		if (KeyEvent.metaStateHasNoModifiers(filteredMetaState)) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_A:
				if (canSelectText()) {
					return onTextContextMenuItem(ID_SELECT_ALL);
				}
				break;
			case KeyEvent.KEYCODE_X:
				if (canCut()) {
					return onTextContextMenuItem(ID_CUT);
				}
				break;
			case KeyEvent.KEYCODE_C:
				if (canCopy()) {
					return onTextContextMenuItem(ID_COPY);
				}
				break;
			case KeyEvent.KEYCODE_V:
				if (canPaste()) {
					return onTextContextMenuItem(ID_PASTE);
				}
				break;
			}
		}
		return super.onKeyShortcut(keyCode, event);
	}

	/**
	 * Unlike {@link #textCanBeSelected()}, this method is based on the <i>current</i> state of the TextView.
	 * {@link #textCanBeSelected()} has to be true (this is one of the conditions to have a selection controller (see
	 * {@link Editor#prepareCursorControllers()}), but this is not sufficient.
	 */
	private boolean canSelectText() {
		return mText.length() != 0;
	}

	/**
	 * Test based on the <i>intrinsic</i> charateristics of the TextView. The text must be spannable and the movement
	 * method must allow for arbitary selection.
	 * 
	 * See also {@link #canSelectText()}.
	 */
	boolean textCanBeSelected() {
		// prepareCursorController() relies on this method.
		// If you change this condition, make sure prepareCursorController is called anywhere
		// the value of this condition might be changed.
		if (mMovement == null || !mMovement.canSelectArbitrarily())
			return false;
		return isTextEditable() || (isTextSelectable() && mText instanceof Spannable && isEnabled());
	}

	/**
	 * Returns whether this text view is a current input method target. The default implementation just checks with
	 * {@link InputMethodManager}.
	 */
	public boolean isInputMethodTarget() {
		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		return imm != null && imm.isActive(this);
	}

	static final int ID_SELECT_ALL = android.R.id.selectAll;
	static final int ID_CUT = android.R.id.cut;
	static final int ID_COPY = android.R.id.copy;
	static final int ID_PASTE = android.R.id.paste;

	/**
	 * Called when a context menu option for the text view is selected. Currently this will be one of
	 * {@link android.R.id#selectAll}, {@link android.R.id#cut}, {@link android.R.id#copy} or {@link android.R.id#paste}
	 * .
	 * 
	 * @return true if the context menu item action was performed.
	 */
	public boolean onTextContextMenuItem(int id) {
		int min = 0;
		int max = mText.length();

		if (isFocused()) {
			final int selStart = getSelectionStart();
			final int selEnd = getSelectionEnd();

			min = Math.max(0, Math.min(selStart, selEnd));
			max = Math.max(0, Math.max(selStart, selEnd));
		}

		switch (id) {
		case ID_SELECT_ALL:
			// This does not enter text selection mode. Text is highlighted, so that it can be
			// bulk edited, like selectAllOnFocus does. Returns true even if text is empty.
			selectAllText();
			return true;

		case ID_PASTE:
			paste(min, max);
			return true;

		case ID_CUT:
			setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
			deleteText_internal(min, max);
			return true;

		case ID_COPY:
			setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
			return true;
		}
		return false;
	}

	CharSequence getTransformedText(int start, int end) {
		// return removeSuggestionSpans(mTransformed.subSequence(start, end));
		return mTransformed.subSequence(start, end);
	}

	@Override
	public boolean performLongClick() {
		boolean handled = false;

		if (super.performLongClick()) {
			handled = true;
		}

		if (true) {
			handled |= mEditor.performLongClick(handled);
		}

		if (handled) {
			performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			mEditor.mDiscardNextActionUp = true;
		}

		return handled;
	}

	@Override
	protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
		super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
		if (true) {
			mEditor.onScrollChanged();
		}
	}

	boolean canCut() {
		return mText.length() > 0 && hasSelection() && mText instanceof Editable && true
				&& mEditor.mKeyListener != null;
	}

	boolean canCopy() {
		return mText.length() > 0 && hasSelection();
	}

	boolean canPaste() {
		return (mText instanceof Editable && mEditor.mKeyListener != null && getSelectionStart() >= 0
				&& getSelectionEnd() >= 0 && ((ClipboardManager) getContext().getSystemService(
				Context.CLIPBOARD_SERVICE)).hasPrimaryClip());
	}

	boolean selectAllText() {
		final int length = mText.length();
		Selection.setSelection((Spannable) mText, 0, length);
		return length > 0;
	}

	/**
	 * Prepare text so that there are not zero or two spaces at beginning and end of region defined by [min, max] when
	 * replacing this region by paste. Note that if there were two spaces (or more) at that position before, they are
	 * kept. We just make sure we do not add an extra one from the paste content.
	 */
	long prepareSpacesAroundPaste(int min, int max, CharSequence paste) {
		if (paste.length() > 0) {
			if (min > 0) {
				final char charBefore = mTransformed.charAt(min - 1);
				final char charAfter = paste.charAt(0);

				if (Character.isSpaceChar(charBefore) && Character.isSpaceChar(charAfter)) {
					// Two spaces at beginning of paste: remove one
					final int originalLength = mText.length();
					deleteText_internal(min - 1, min);
					// Due to filters, there is no guarantee that exactly one character was
					// removed: count instead.
					final int delta = mText.length() - originalLength;
					min += delta;
					max += delta;
				} else if (!Character.isSpaceChar(charBefore) && charBefore != '\n'
						&& !Character.isSpaceChar(charAfter) && charAfter != '\n') {
					// No space at beginning of paste: add one
					final int originalLength = mText.length();
					replaceText_internal(min, min, " ");
					// Taking possible filters into account as above.
					final int delta = mText.length() - originalLength;
					min += delta;
					max += delta;
				}
			}

			if (max < mText.length()) {
				final char charBefore = paste.charAt(paste.length() - 1);
				final char charAfter = mTransformed.charAt(max);

				if (Character.isSpaceChar(charBefore) && Character.isSpaceChar(charAfter)) {
					// Two spaces at end of paste: remove one
					deleteText_internal(max, max + 1);
				} else if (!Character.isSpaceChar(charBefore) && charBefore != '\n'
						&& !Character.isSpaceChar(charAfter) && charAfter != '\n') {
					// No space at end of paste: add one
					replaceText_internal(max, max, " ");
				}
			}
		}

		return TextUtils.packRangeInLong(min, max);
	}

	/**
	 * Paste clipboard content between min and max positions.
	 */
	private void paste(int min, int max) {
		ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = clipboard.getPrimaryClip();
		if (clip != null) {
			boolean didFirst = false;
			for (int i = 0; i < clip.getItemCount(); i++) {
				CharSequence paste = clip.getItemAt(i).coerceToStyledText(getContext());
				if (paste != null) {
					if (!didFirst) {
						long minMax = prepareSpacesAroundPaste(min, max, paste);
						min = TextUtils.unpackRangeStartFromLong(minMax);
						max = TextUtils.unpackRangeEndFromLong(minMax);
						Selection.setSelection((Spannable) mText, max);
						((Editable) mText).replace(min, max, paste);
						didFirst = true;
					} else {
						((Editable) mText).insert(getSelectionEnd(), "\n");
						((Editable) mText).insert(getSelectionEnd(), paste);
					}
				}
			}
			LAST_CUT_OR_COPY_TIME = 0;
		}
	}

	private void setPrimaryClip(ClipData clip) {
		ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
		clipboard.setPrimaryClip(clip);
		LAST_CUT_OR_COPY_TIME = SystemClock.uptimeMillis();
	}

	/**
	 * Get the character offset closest to the specified absolute position. A typical use case is to pass the result of
	 * {@link MotionEvent#getX()} and {@link MotionEvent#getY()} to this method.
	 * 
	 * @param x
	 *            The horizontal absolute position of a point on screen
	 * @param y
	 *            The vertical absolute position of a point on screen
	 * @return the character offset for the character whose position is closest to the specified position. Returns -1 if
	 *         there is no layout.
	 */
	public int getOffsetForPosition(float x, float y) {
		if (getLayout() == null)
			return -1;
		final int line = getLineAtCoordinate(y);
		final int offset = getOffsetAtCoordinate(line, x);
		return offset;
	}

	float convertToLocalHorizontalCoordinate(float x) {
		x -= getTotalPaddingLeft();
		// Clamp the position to inside of the view.
		x = Math.max(0.0f, x);
		x = Math.min(getWidth() - getTotalPaddingRight() - 1, x);
		x += getScrollX();
		return x;
	}

	int getLineAtCoordinate(float y) {
		y -= getTotalPaddingTop();
		// Clamp the position to inside of the view.
		y = Math.max(0.0f, y);
		y = Math.min(getHeight() - getTotalPaddingBottom() - 1, y);
		y += getScrollY();
		return getLayout().getLineForVertical((int) y);
	}

	private int getOffsetAtCoordinate(int line, float x) {
		x = convertToLocalHorizontalCoordinate(x);
		return getLayout().getOffsetForHorizontal(line, x);
	}

	@Override
	public boolean onDragEvent(DragEvent event) {
		switch (event.getAction()) {
		case DragEvent.ACTION_DRAG_STARTED:
			return mEditor.hasInsertionController();

		case DragEvent.ACTION_DRAG_ENTERED:
			TextView.this.requestFocus();
			return true;

		case DragEvent.ACTION_DRAG_LOCATION:
			final int offset = getOffsetForPosition(event.getX(), event.getY());
			Selection.setSelection((Spannable) mText, offset);
			return true;

		case DragEvent.ACTION_DROP:
			mEditor.onDrop(event);
			return true;

		case DragEvent.ACTION_DRAG_ENDED:
		case DragEvent.ACTION_DRAG_EXITED:
		default:
			return true;
		}
	}

	boolean isInBatchEditMode() {
		final Editor.InputMethodState ims = mEditor.mInputMethodState;
		if (ims != null) {
			return ims.mBatchEditNesting > 0;
		}
		return mEditor.mInBatchEditControllers;
	}

	/**
	 * @hide
	 */
	protected void viewClicked(InputMethodManager imm) {
		if (imm != null) {
			imm.viewClicked(this);
		}
	}

	/**
	 * Deletes the range of text [start, end[.
	 * 
	 * @hide
	 */
	protected void deleteText_internal(int start, int end) {
		((Editable) mText).delete(start, end);
	}

	/**
	 * Replaces the range of text [start, end[ by replacement text
	 * 
	 * @hide
	 */
	protected void replaceText_internal(int start, int end, CharSequence text) {
		((Editable) mText).replace(start, end, text);
	}

	/**
	 * Sets a span on the specified range of text
	 * 
	 * @hide
	 */
	protected void setSpan_internal(Object span, int start, int end, int flags) {
		((Editable) mText).setSpan(span, start, end, flags);
	}

	/**
	 * Moves the cursor to the specified offset position in text
	 * 
	 * @hide
	 */
	protected void setCursorPosition_internal(int start, int end) {
		Selection.setSelection(((Editable) mText), start, end);
	}

	private class ChangeWatcher implements TextWatcher, SpanWatcher {

		// private CharSequence mBeforeText;

		public void beforeTextChanged(CharSequence buffer, int start, int before, int after) {
			if (DEBUG_EXTRACT)
				Log.v(LOG_TAG, "beforeTextChanged start=" + start + " before=" + before + " after=" + after + ": "
						+ buffer);
			TextView.this.sendBeforeTextChanged(buffer, start, before, after);
		}

		public void onTextChanged(CharSequence buffer, int start, int before, int after) {
			if (DEBUG_EXTRACT)
				Log.v(LOG_TAG, "onTextChanged start=" + start + " before=" + before + " after=" + after + ": " + buffer);
			TextView.this.handleTextChanged(buffer, start, before, after);
		}

		public void afterTextChanged(Editable buffer) {
			if (DEBUG_EXTRACT)
				Log.v(LOG_TAG, "afterTextChanged: " + buffer);
			TextView.this.sendAfterTextChanged(buffer);

			// if (MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SELECTING) != 0) {
			// MetaKeyKeyListener.stopSelecting(TextView.this, buffer);
			// }
		}

		public void onSpanChanged(Spannable buf, Object what, int s, int e, int st, int en) {
			if (DEBUG_EXTRACT)
				Log.v(LOG_TAG, "onSpanChanged s=" + s + " e=" + e + " st=" + st + " en=" + en + " what=" + what + ": "
						+ buf);
			TextView.this.spanChange(buf, what, s, st, e, en);
		}

		public void onSpanAdded(Spannable buf, Object what, int s, int e) {
			if (DEBUG_EXTRACT)
				Log.v(LOG_TAG, "onSpanAdded s=" + s + " e=" + e + " what=" + what + ": " + buf);
			TextView.this.spanChange(buf, what, -1, s, -1, e);
		}

		public void onSpanRemoved(Spannable buf, Object what, int s, int e) {
			if (DEBUG_EXTRACT)
				Log.v(LOG_TAG, "onSpanRemoved s=" + s + " e=" + e + " what=" + what + ": " + buf);
			TextView.this.spanChange(buf, what, s, -1, e, -1);
		}
	}
}
