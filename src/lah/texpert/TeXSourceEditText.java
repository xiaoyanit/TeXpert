package lah.texpert;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * Extension of {@link EditText} to support editing of TeX and bibtex source
 * code such as syntax highlighting.
 */
public class TeXSourceEditText extends EditText {
	
	public TeXSourceEditText(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	public TeXSourceEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public TeXSourceEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}
	
	int ColorStandard = Color.parseColor("#FFFFFF");
	int ColorComment = Color.parseColor("#606060");
	int ColorMath = Color.parseColor("#008000");
	int ColorCommand = Color.parseColor("#800000");
	int ColorKeyword =Color.parseColor("#0000CC");
	int ColorVerbatim = Color.parseColor("#9A4D00");
	int ColorTodo = Color.parseColor("#FF0000");
	int ColorKeywordGraphic = Color.parseColor("#006699");
	int ColorNumberGraphic = Color.parseColor("#660066");
	
	String[] KeyWords= "section{,subsection{,subsubsection{,chapter{,part{,paragraph{,subparagraph{,section*{,subsection*{,subsubsection*{,chapter*{,part*{,paragraph*{,subparagraph*{,label{,includegraphics{,includegraphics[,includegraphics*{,includegraphics*[,include{,input{,begin{,end{".split(",");
	
	

}
