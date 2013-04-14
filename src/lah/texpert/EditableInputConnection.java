package lah.texpert;

import android.text.Editable;
import android.text.method.KeyListener;
import android.util.Log;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;

public class EditableInputConnection extends BaseInputConnection {

	private static final boolean DEBUG = false;

	private static final String TAG = "EditableInputConnection";

	// Keeps track of nested begin/end batch edit to ensure this connection always has a
	// balanced impact on its associated TextView.
	// A negative value means that this connection has been finished by the InputMethodManager.
	private int mBatchEditNesting;

	private final TextView mTextView;

	public EditableInputConnection(TextView textview) {
		super(textview, true);
		mTextView = textview;
	}

	@Override
	public boolean beginBatchEdit() {
		synchronized (this) {
			if (mBatchEditNesting >= 0) {
				mTextView.beginBatchEdit();
				mBatchEditNesting++;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean clearMetaKeyStates(int states) {
		final Editable content = getEditable();
		if (content == null)
			return false;
		KeyListener kl = mTextView.getKeyListener();
		if (kl != null) {
			try {
				kl.clearMetaKeyState(mTextView, content, states);
			} catch (AbstractMethodError e) {
				// This is an old listener that doesn't implement the
				// new method.
			}
		}
		return true;
	}

	@Override
	public boolean commitCompletion(CompletionInfo text) {
		if (DEBUG)
			Log.v(TAG, "commitCompletion " + text);
		mTextView.beginBatchEdit();
		mTextView.onCommitCompletion(text);
		mTextView.endBatchEdit();
		return true;
	}

	@Override
	public boolean endBatchEdit() {
		synchronized (this) {
			if (mBatchEditNesting > 0) {
				// When the connection is reset by the InputMethodManager and reportFinish
				// is called, some endBatchEdit calls may still be asynchronously received from the
				// IME. Do not take these into account, thus ensuring that this IC's final
				// contribution to mTextView's nested batch edit count is zero.
				mTextView.endBatchEdit();
				mBatchEditNesting--;
				return true;
			}
		}
		return false;
	}

	@Override
	public Editable getEditable() {
		TextView tv = mTextView;
		if (tv != null) {
			return tv.getEditableText();
		}
		return null;
	}

	@Override
	public boolean performEditorAction(int actionCode) {
		if (DEBUG)
			Log.v(TAG, "performEditorAction " + actionCode);
		mTextView.onEditorAction(actionCode);
		return true;
	}

}
