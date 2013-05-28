TeXpert
=======

LaTeX document preparation app for Android

Donate version is now available on [Play store][0]. To build it from source, see instruction at the end of the file.

This app aims to be a TeX document development environment application like
TeXMaker or TeXworks on PC.

Features
--------

The target of TeXpert is for _tablets_ like Nexus 7:

 * lack physical keyboard
 * reasonably big screen for document editing
 * modern Android (4.2+)
 
Phone is not supported. I contemplate the following key features:

1. [ ] Efficient input: _complementing_, *not replacing*, useful existing tools
   like [SwiftKey][1]
 * [x] shortcut to insert commonly used TeX characters such as `{`, `}`
 * [x] shortcut to insert frequently-used and user-defined commands via 
   `\newcommand` (assuming that if the user defines a command, he/she wants to 
   use it frequently in the document)
 * [ ] shortcut to insert labels, references
 * [ ] create document from templates
1. [ ] Easy navigation
 * [x] Link to document sections
 * [ ] Link to beamer frame
1. [ ] Smart correction suggestion
 * `Begin align` --> `\begin{align}`
 * `right arrow` --> `\rightarrow`
1. [ ] Integration with [TeXPortal][4]
 * [x] load log file
 * [x] open associated PDF
 * [x] annotate generated log
 * [ ] link log line to source
 * [x] clean up generated files
1. [x] Basic syntax highlighting: __NO__ consideration for 
 * ``\catcode`\[char]=[num]`` (cf. [this Wiki page][5])
 * `verbatim` environment
 * complicated context-free grammar styling (e.g. math formulas in `$$ ... $$`)
 > Being a Turing-complete macro expansion programming language, TeX actually 
 > has **NO** grammar in the first place! Sometimes ago, I have tried to look
 > for parsing part in TeX source code but (to my surprise) there was none.
1. [ ] Document reorganization (inspired by Eclipse's refactoring features) such as
 * [ ] move `\newcommand` to preamble
 * [ ] rename defined command (in a context-sensitive way)
 * [ ] format the source code
       E.g. separate displayed math, `\begin`, `\end`, `\item`, or 
       line comments to new line.
 * [ ] convert `$$ ... $$` to the recommended `\[ ... \]`
 * [ ] textually align the array-kind environment

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
internal database; resulting in sluggish and noticeable delay before the  
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

Build Instruction
-----------------

 * Compile [MuPDF][6] library:
    - Modified files are in `mupdf/` subdirectory. Main changes are:
       * Append `LOCAL_CFLAGS` with `-DNOCJK -DNODROIDFONT` and remove XPS/CBZ-related source in `android/jni/Core.mk`
       * Various tweaks to `Application.mk` following optimization tips from http://blog.algolia.com/android-ndk-how-to-reduce-libs-size/
       * Simplify implementations of functions `fz_open_document` and `fz_open_document_with_stream` in `fitz/doc_document.c`: remove XPS/CBZ handling & related `extern` functions
       * Change package, remove unused functions in `android/jni/mupdf.c`
    - Follow the build instruction of MuPDF i.e. execute `make generate` and then `ndk-build` in the `android/`. The shared libraries are in `android/libs/`. Just copy them to TeXpert.
    - **Note**: All directories mentioned here are relative to root of extracted MuPDF source package.
 * Clone my projects [LAHIndex][7] and [LAHWidgets][8]
 * Import to Eclipse and build

[0]: https://play.google.com/store/apps/details?id=lah.texpert
[1]: http://www.swiftkey.net/
[2]: http://www.swype.com/
[3]: http://www.android.com/whatsnew/
[4]: https://play.google.com/store/apps/details?id=lah.texportal
[5]: http://en.wikibooks.org/wiki/TeX/catcode
[6]: http://www.mupdf.com/
[7]: https://github.com/anhoavu/LAHIndex
[8]: https://github.com/anhoavu/LAHWidgets
