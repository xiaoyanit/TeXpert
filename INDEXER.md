Indexer
=======

Traditional syntax highlighting is performed via finding and styling pattern
in the (programming) language such as keywords, literal strings, control flow
blocks, etc. Simple syntax highlighter operates at regular expression level 
while complicated ones (like Eclipse) can operate at the grammar level. The
key challenge is to provide responsiveness which reduce to the problem of
**quickly pick out the highlighting blocks** i.e. text searching.

Capability limitation of Android-running devices make it not suitable to 
provide grammar-level syntax highlighting or reproduce the whole processing
mechanism of TeX. Therefore, I decided to supply _regex-based highlighter_
only.

Universal solution to text searching (cf. Google) is by **text indexing**:
scan through the text to produce a mapping of a pattern (e.g. a word) to 
all locations of its occurrences. The problem is significantly more challenging
when the text changes (such as after user edits) in which case we have to
promptly update the index!

To make the problem easier, TeXpert supports only the following highlighting:

 * Line comment in gray color
 * Command sequences in blue: backslash followed by letters
 * Special symbols

Solution using dynamic character indexer
----------------------------------------

To provide the above highlighting, I devise a dynamic character-indexing 
algorithm which is just sufficient:

 * Line comment can be quickly pick up by iterating through occurrences of 
   `%` on each text line and pick up the first _unescaped_ one. The comment spans 
   either to the end of line or end of file.
 * Given that any comment block is taken care of, we can simply iterate through
   special characters. If a backslash is encountered, whether it should be treated
   as the start of a command or just a single special character depend solely on
   the next character.
   
Note that the assumption that no `\catcode` is used here!
   
From the above description, the main function which the dynamic character 
indexer is expected to provide is to **quickly provide the occurrence positions 
of a class of characters** within a range of text AND can efficiently update 
itself after text edits.

Let `t` be a TeX string which we want to highlight.

Given existence of indexers, to pick out the comment blocks in the substring
`t[s..e)` of a single text line, we first need to extend the range to a full 
line `t[s1..e1)` using a LineFeed indexer: just pick up the first occurrence of
'\n' after e. Then, use the '%'-indexer to pick out occurrences of '%' within 
`t[s1..e1)` and iterate through them. Suppose that s1 <= k < e1 is the first 
occurrence of '%' which is not escaped i.e. `t[k-1]` is not `\`. Then we know
that the line `t[s1..e1)` has a comment block from [k..e1).

With the line comment settled, we use a special-char-indexer to pick out
all special chars in `t[s1..k)` and annotate the text. We are done!

The whole highlighting process requires three corroborating indexers, indexing
`%`, `\n` and special chars in `\`, `{`, `}`, etc.

Implementation
--------------

Keeping track of patterns is essentially provide a mapping `m : N --> N` where 
`m(x)` is defined as the position of x-th occurrence of the pattern. For example,
given `t = abcabcabc` and we want to index character `a` then we need to supply
a map `m` where `m(0)=0, m(1)=3, m(2)=6` and `m(x)` is INFINITE when `x > 3`.
Then we can find occurrences of `a` within a range, say `t[2..8)` by finding out
the order of the first occurrences after 2 and 8; formally: `argmin_{z} (m(z) >= 2)` 
and `argmin_{z} (m(z) >= 8)` In this case, evaluations of the above expressions
give `1` and `3` respectively. This means, the first occurrences of `a` after
positions 2 and 8 are the 1-st and 3-rd occurrences of `a` in the entire string.
Therefore, occurrences of `a` in `t[2..8)` are the [1..3)-th occurrences which
are at positions m(1), m(2) in the text. This search is very fast given the
sorted nature of the map.

Efficient data structure for `m` is perhaps a one-dimensional array. To support
fast update after edit, we make it a split array i.e. the entries are stored
in two segments `[0..x) U [y..m.length)` where values in the second segment
is added by some `d`. Given `x, y, d`, the actual mapping `m` is defined as
`m(z) = m[z]` if `z < x` and `m(z) = m[y+z-x]` if `x <= z`.

General Pattern Indexing
------------------------