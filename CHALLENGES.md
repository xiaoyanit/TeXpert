The Syntax Highlighting Challenge
=================================

In the official API, Android developers are supplied with some simple text 
widgets for the purpose of displaying to and interacting with users such as
[EditText][1].
Styling text is achieved by preparing the display text with instances of 
[SpannableStringBuilder][2]
and setting the styled region (i.e. spanning) with objects in package
[android.tex.style][3].
  
Unfortunately, naive uses of the API will *not* solve the syntax highlighting 
problem satisfactorily. The reason is the lack of application responsiveness when the source is too long.
After reading the related [source code][4]
(mainly packages `android.text.*` and `android.widgets.*`) and using [traceview][6] 
profiling tool, I can pinpoint the source of such unresponsiveness. Let me
abridge the detail in the following paragraphs.

Traceview shows that most of the CPU time is for rendering (i.e. drawing) 
the text which eventually is handled by [Layout][5]
object (for editable text, the instance used is actually of subclass [DynamicLayout][7]).

The app always lags right after user edits texts with long content with too 
many styles. Logically, when edit occurs, Android must have a mechanism to `redraw`
the display. In GUI-term, it is usually done by notifying the view. My plausible 
guess is that too much `redraw` requests is performed. Digging deeper, one 
realizes that when user edits the content,
the underlying content is updated via `replace` method, for example, of 
[SpannableStringBuilder][2] (let me skip 
the full mechanism here which involves complicated way to handle soft vs. hard 
keyboard).

A close investigation of [SpannableStringBuilder source code][8] shows 
that instances of `TextWatcher` and `SpanWatcher` that is previously bound will be
notified. There are a number of implementation for these interfaces and the most
important one is implemented as an internal class of [DynamicLayout][7] which
basically invokes its `reflow(CharSequence s, int where, int before, int after)` 
method to redraw the text. [SpannableStringBuilder][8]'s method which performs
notification is `sendToSpanWatchers(int replaceStart, int replaceEnd, int nbNewChars)`
whose implementation _iterates through every single span_! BINGO! This explains why
refreshing is slow when there are lots of styling elements applied. 

(At first, I think it is stupid; but then I realize that that is the only 
thing they can do. Think of the situation when there are overlapping styles
in which case the lastly added style must have the priority.)

Beside this responsiveness issue, the OS imposes certain restrictions such 
as 16/24/32MB app heap memory limit which I discovered earlier. This whole
discussion is only meant to prove that:

> Writing syntax highlighter in Android is HARD.

[1]: http://developer.android.com/reference/android/widget/EditText.html
[2]: http://developer.android.com/reference/android/text/SpannableStringBuilder.html
[3]: http://developer.android.com/reference/android/text/style/package-summary.html
[4]: https://github.com/android/platform_frameworks_base/tree/master/core/java
[5]: https://github.com/android/platform_frameworks_base/blob/master/core/java/android/text/Layout.java
[6]: http://developer.android.com/tools/debugging/debugging-tracing.html
[7]: https://github.com/android/platform_frameworks_base/blob/master/core/java/android/text/DynamicLayout.java
[8]: https://github.com/android/platform_frameworks_base/blob/master/core/java/android/text/SpannableStringBuilder.java
 