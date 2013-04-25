TeXpert
=======

LaTeX document preparation app for Android

This app aims to be a TeX document development environment application like 
TeXMaker or TeXworks on PC.

Features
--------

The target of TeXpert is for _tablets_ like Nexus 7:

 * lack physical keyboard
 * reasonably big screen for document editing
 * modern Android (4.2+)
 
Phone is not supported. I expect the following key features:

1. efficient input: _complementing_, *not replacing*, useful existing tools like [SwiftKey][1];
1. smart correction suggestion;
1. button to compile with [TeXPortal][4]; and
1. basic syntax highlighting: *NO* consideration for 
 * ``\catcode`\[char]=[num]`` (cf. [this Wiki page][5]);
 * `verbatim` environment;
 * complicated context-free grammar styling (e.g. math formulas in `$$ ... $$`).
 
These features are ordered based on perceived importance.

Unlike their PC counterparts where one can easily type in the content.
The unavailability of physical keyboard makes it substantially more 
laborious to write on Nexus 7. Many text editor tends to treat LaTeX editing 
as a special case of code editing. This is a prevalent viewpoint on PC. 
Yet I believe that source code and LaTeX editing are fairly different things.

What SwiftKey has to offer? What it lacks?
------------------------------------------

Availability of efficient input methods (IMEs or keyboards) is a great
distinguishing factor of Android from other mobile platforms. Examples are 
[SwiftKey][1], [Swype][2] and default IME of Jelly Bean 4.2.

[SwiftKey][1] has input prediction based on usage history, email messages, 
social media, etc. Unfortunately, this requires significant processing power 
and thus SwiftKey constantly crashes, especially on long texts. (It seems that 
whenever it is invoked, it reads the whole input field and update its 
internal database; resulting in sluggish and noticable delay before the  
keyboard is shown up.) [Android 4.2][3] has a great improvement in its IMEs. 
It is much faster than SwiftKey and also has support for [Swype][2] gesture 
typing.

These IMEs are useful to input textual inputs. Yet, TeX documents typically
consist of significant amount of special control characters, such as backslash
`\`, curly braces `{` and `}`, or math shift `$`. To type these symbols, one 
usually have to press 2-3 buttons on the IME.

Technical Limitations
---------------------

In most situation, TeXpert only supports files up to 2MB.

[1]: http://www.swiftkey.net/
[2]: http://www.swype.com/
[3]: http://www.android.com/whatsnew/
[4]: https://play.google.com/store/apps/details?id=lah.texportal
[5]: http://en.wikibooks.org/wiki/TeX/catcode