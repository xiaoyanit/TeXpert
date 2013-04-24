TeXpert
=======

LaTeX document preparation app for Android

This app aims to be a TeX document development environment application like 
TeXMaker or TeXworks on PC.

The target of TeXpert is for _tablets_ like Nexus 7:

 * lack physical keyboard
 * reasonably big screen for document editing
 * modern Android (4.2+)
 
Phone is not supported. I expect the following key features:

1. efficient input: _complementing_, *not replacing*, useful existing tools like _SwiftKey_;
1. smart correction suggestion;
1. button to compile with TeXPortal; and
1. basic syntax highlighting.
 * _NO_ consideration for ``\catcode`\[char]=[num]`` (cf. [this Wiki page](http://en.wikibooks.org/wiki/TeX/catcode))
 * _NO_ consideration for `verbatim` environment
 * _NO_ complicated context-free grammar styling (e.g. formulas in $$ ... $$)
 
These features are ordered based on perceived importance.

Unlike their PC counterparts where one can easily type in the content.
The unavailability of physical keyboard makes it substantially more 
laborious to write on Nexus 7. Many text editor tends to treat LaTeX editing 
as a special case of code editing. This is a prevalent viewpoint on PC. 
Yet I believe that source code and LaTeX editing are fairly different things.

What SwiftKey has to offer? What it lacks?
------------------------------------------

SwiftKey and many other Input Methods (IMEs) for Android are apps that greatly
distinguishes Android from other mobile OSes. The key feature is input prediction
based on input history, email messages, social media, etc. Unfortunately, this
requires significant processing power and thus SwiftKey constantly crashes,
especially on long texts. (It seems that whenever it is invoked, it reads the
whole input field and update its internal database; resulting in sluggish 
and delay before the keyboard is shown up.)

Android 4.2 has a great improvement in its IMEs. It is much faster than SwiftKey 
and has support for Swype gesture input.
