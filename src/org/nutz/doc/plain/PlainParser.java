package org.nutz.doc.plain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nutz.doc.Code;
import org.nutz.doc.Including;
import org.nutz.doc.Line;
import org.nutz.doc.Doc;
import org.nutz.doc.DocParser;
import org.nutz.doc.Inline;
import org.nutz.doc.Media;
import org.nutz.doc.OrderedListItem;
import org.nutz.doc.Refer;
import org.nutz.doc.UnorderedListItem;
import org.nutz.doc.style.FontStyle;
import org.nutz.lang.Lang;
import org.nutz.lang.Streams;
import org.nutz.lang.Strings;
import org.nutz.lang.util.LinkedCharArray;

public class PlainParser implements DocParser {

	@Override
	public Doc parse(InputStream ins) {
		/*
		 * Prepare the reader
		 */
		BufferedReader br;
		try {
			br = new BufferedReader(new InputStreamReader(ins, "UTF-8"));
		} catch (UnsupportedEncodingException e1) {
			throw Lang.wrapThrow(e1);
		}
		/*
		 * Parepare document
		 */
		Doc doc = new Doc();
		String line;
		Line b = doc.root();
		try {
			while (null != (line = br.readLine())) {
				LinekWrapper bw = parseLine(br, line);
				if (!(bw.line instanceof RootLine))
					if (!(bw.line instanceof Including))
						if (bw.line.isBlank()) {
							if (b.hasParent() || (b.hasParent() && b.isBlank()))
								b = b.parent();
							b.addChild(bw.line);
							b = bw.line;
							continue;
						}
				// find the parent to append
				while (b.hasParent() && b.deep() > bw.deep) {
					b = b.parent();
				}
				if (bw.line instanceof RootLine) {
					for (Iterator<Line> it = ((RootLine) bw.line).root.childIterator(); it
							.hasNext();) {
						b.addChild(it.next());
					}
				} else {
					if (b.deep() < bw.deep)
						bw.line.insert(Strings.dup('\t', bw.deep - b.deep()));
					b.addChild(bw.line);
					b = bw.line;
				}
			}
		} catch (IOException e) {
			throw Lang.wrapThrow(e);
		}
		return doc;
	}

	private static class LinekWrapper {
		Line line;
		int deep;
	}

	private static class RootLine extends Line {
		private Line root;

		RootLine(Line root) {
			this.root = root;
		}
	}

	private LinekWrapper parseLine(BufferedReader reader, String line) {
		LinekWrapper lw = new LinekWrapper();
		char[] cs = line.toCharArray();
		for (; lw.deep < cs.length; lw.deep++)
			if (cs[lw.deep] != '\t')
				break;
		lw.line = parseInlines(reader, lw.deep, new String(cs, lw.deep, cs.length - lw.deep));
		return lw;
	}

	private static Pattern INCLUDE = Pattern.compile("^@[>]?include:", Pattern.CASE_INSENSITIVE);
	private static Pattern CODESTART = Pattern.compile("^([{]{3})(<[a-zA-Z]+>)");
	private static Pattern CODEEND = Pattern.compile("[}]{3}$");
	private static Pattern INDEXTABLE = Pattern.compile("^(#index:)([1-9])",
			Pattern.CASE_INSENSITIVE);
	private static Pattern OL = Pattern.compile("^(#[\\s]+)(.*)$");
	private static Pattern UL = Pattern.compile("^([*][\\s]+)(.*)$");

	private Line parseInlines(BufferedReader reader, int deep, String s) {
		/*
		 * The line is for include something
		 */
		Matcher matcher = INCLUDE.matcher(s);
		if (matcher.find()) {
			String rs = Strings.trim(s.substring(matcher.end()));
			Refer re = Doc.refer(rs);
			if (null == re.getFile() || !re.getFile().exists()) {
				throw Lang.makeThrow("Fail to find doc file '%s'!!!", re.getFile()
						.getAbsolutePath());
			}
			if (s.startsWith("@>")) {
				return Doc.including(re, this);
			} else {
				try {
					InputStream ins = Streams.fileIn(re.getFile());
					Doc doc = this.parse(ins);
					ins.close();
					return new RootLine(doc.root());
				} catch (IOException e) {
					throw Lang.wrapThrow(e);
				}
			}
		}
		/*
		 * The line is for code zzh: the real code should not appear in same
		 * line of the CODESTART
		 */
		matcher = CODESTART.matcher(Strings.trim(s));
		if (matcher.find()) {
			StringBuilder sb = new StringBuilder();
			Code.TYPE type = null;
			// Get the code type
			if (matcher.groupCount() == 2) {
				String tstr = matcher.group(2);
				tstr = tstr.substring(1, tstr.length() - 1);
				type = Code.TYPE.valueOf(tstr);
			}
			// read line
			// and the CODEEND should not appear in the same line of real code.
			String line;
			try {
				while (null != (line = reader.readLine())) {
					if (CODEEND.matcher(line).find())
						break;
					int pos;
					for (pos = 0; pos < deep; pos++)
						if (line.charAt(pos) != '\t')
							break;
					sb.append(line.substring(pos)).append('\n');
				}
				if (sb.length() > 0)
					sb.deleteCharAt(sb.length() - 1);
			} catch (IOException e) {
				throw Lang.wrapThrow(e);
			}
			return Doc.code(sb.toString(), type);
		}
		/*
		 * The line is a index table
		 */
		matcher = INDEXTABLE.matcher(s);
		if (matcher.find()) {
			return Doc.indexTable(Integer.valueOf(matcher.group(2)));
		}
		/*
		 * The line is contains a group of text
		 */
		List<Inline> inlines = new ArrayList<Inline>();
		LinkedCharArray lca = new LinkedCharArray();
		StringBuilder sb = new StringBuilder();
		Class<? extends Line> lineType;
		char[] cs;
		matcher = UL.matcher(s);
		if (matcher.find()) {
			cs = matcher.group(2).toCharArray();
			lineType = UnorderedListItem.class;
		} else {
			matcher = OL.matcher(s);
			if (matcher.find()) {
				cs = matcher.group(2).toCharArray();
				lineType = OrderedListItem.class;
			} else {
				cs = s.toCharArray();
				lineType = Line.class;
			}
		}
		for (char c : cs) {
			switch (c) {
			case '{':
				if (lca.last() == '{') {
					sb.append(lca.clear());
				} else {
					if (lca.size() > 0)
						sb.append(lca.clear());
					if (sb.length() > 0) {
						inlines.add(toInline(sb.toString()));
						sb = new StringBuilder();
					}
					lca.push(c);
				}
				break;
			case '}':
				if (lca.first() == '{') {
					sb.append(lca.push(c).clear());
					inlines.add(toInline(sb.toString()));
					sb = new StringBuilder();
				} else {
					lca.push(c);
				}
				break;
			case '[':
				if (lca.first() == '{') {
					lca.push(c);
				} else if (lca.last() == '[') {
					sb.append(lca.clear());
				} else {
					if (lca.size() > 0)
						sb.append(lca.clear());
					if (sb.length() > 0) {
						inlines.add(toInline(sb.toString()));
						sb = new StringBuilder();
					}
					lca.push(c);
				}
				break;
			case ']':
				if (lca.first() == '{') {
					lca.push(c);
				} else if (lca.first() == '[') {
					lca.push(c);
					sb.append(lca.clear());
					inlines.add(toInline(sb.toString()));
					sb = new StringBuilder();
				} else {
					lca.push(c);
				}
				break;
			default:
				lca.push(c);
			}
		}
		if (lca.size() > 0)
			sb.append(lca.clear());
		if (sb.length() > 0)
			inlines.add(toInline(sb.toString()));
		return Doc.line(lineType, inlines);
	}

	private static Pattern QUOTE = Pattern.compile("^([{])(.*)([}])$");
	private static Pattern MARK = Pattern.compile("^[~_*^,]*");

	private Inline toInline(String s) {
		Matcher m = QUOTE.matcher(s);
		if (m.find()) {
			s = m.group(2);
			m = MARK.matcher(s);
			if (m.find()) {
				String mark = m.group();
				Inline inline = parseInline(s.substring(mark.length()));
				for (char c : mark.toCharArray()) {
					switch (c) {
					case '~':
						inline.getStyle().getFont().addStyle(FontStyle.STRIKE);
						break;
					case '_':
						inline.getStyle().getFont().addStyle(FontStyle.ITALIC);
						break;
					case '*':
						inline.getStyle().getFont().addStyle(FontStyle.BOLD);
						break;
					case '^':
						inline.getStyle().getFont().setAsSup();
						break;
					case ',':
						inline.getStyle().getFont().setAsSub();
						break;
					}
				}
				return inline;
			}
		}
		return parseInline(s);
	}

	private static Pattern LINKS = Pattern.compile("^([\\[])(.*)([\\]])$");

	private Inline parseInline(String s) {
		Matcher m = LINKS.matcher(s);
		if (m.find()) {
			s = m.group(2);
			String[] ss = Strings.splitIgnoreBlank(s, "[ ]");
			if (ss.length == 1) {
				Media media = parseMedia(ss[0]);
				if (null != media)
					return media;
				Inline inline = Doc.inline(ss[0]);
				inline.href(ss[0]);
				return inline;
			} else {
				String txt = ss[1];
				Media media = parseMedia(txt);
				if (null != media) {
					media.href(ss[0]);
					return media;
				}
				Inline inline = Doc.inline(txt);
				inline.href(ss[0]);
				return inline;
			}
		}
		return Doc.inline(s);
	}

	private static Pattern MEDIAS = Pattern.compile(
			"^([/\\\\]|[a-zA-Z]:[/\\\\])?([a-zA-Z0-9_/\\\\])*([.](png|gif|jpeg|jpg))$",
			Pattern.CASE_INSENSITIVE);

	private Media parseMedia(String s) {
		if (MEDIAS.matcher(s).find()) {
			Media m = Doc.media(s);
			m.setText(s);
			return m;
		}
		return null;
	}

	public static void main(String[] args) {
		String s = "@>include:dkd;dldkaf aabc";
		Matcher matcher = INCLUDE.matcher(s);
		matcher.find();
		String refer = Strings.trim(s.substring(matcher.end()));
		System.out.printf("{%s}", refer);
	}
}