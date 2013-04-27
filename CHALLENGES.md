The Syntax Highlighting Challenge
=================================

Styling in Android
------------------

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

Root of Inefficiency
--------------------

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

In fact, looking at other methods of [SpannableStringBuilder][8], one easily
picked up lots of loop-through-all-spans blocks.

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

How existing editor tackle this?
--------------------------------

We have seen that unresponsiveness come from blindly iterate through all styling
objects applied to the text after each edit. So there are at least two ways to
deal with this inherent inefficiency:

1. Limit the number of possible iterations. In particular, since the screen is
   not big enough to contain all the text, we can dynamically add and remove the
   styles based on the currently displayed content. When user navigate (scroll,
   arrow key, etc.), shift the _focusing area_ appropriately.
1. Overriding the behavior so that it does not loop through all styling elements
   whenever the content gets changed. In other words, don't use the API the
   naive way.

The first solution is used by many editors such as [DroidEdit][9]. The
evidence is that when we scroll a big chunk of text, the unshown content
will be highlighted after a noticeable delay. Certainly, this only works if
the displayed region does not already contain a large amount of highlights.

As far as I know, I only believe that the second solution is only used in
[Jota+][10]. To test it, just load a big text file and scroll through it,
there is no noticeable highlighting delay: the styles are already there.

I devise a solution using the second approach. But this is a long story and
I shall describe in another document. (After I implemented this, I realize
that Jota+ claim that it supports 1 million characters should be understood as
an upper limit: it cannot support very much bigger content than that stated
amount.)

[9]: https://play.google.com/store/apps/details?id=com.aor.droidedit
[10]: https://play.google.com/store/apps/details?id=jp.sblo.pandora.jota.plus
