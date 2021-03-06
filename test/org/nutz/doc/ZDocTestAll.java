package org.nutz.doc;

import org.junit.runner.RunWith;

import org.junit.runners.Suite;
import org.nutz.doc.html.HtmlDocRenderTest;
import org.nutz.doc.style.ColorTest;
import org.nutz.doc.zdoc.BlockMakerTest;
import org.nutz.doc.zdoc.MetaScanningTest;
import org.nutz.doc.zdoc.VarContextParserTest;
import org.nutz.doc.zdoc.WhitespaceAndTabTest;
import org.nutz.doc.zdoc.ZDocParserTest;
import org.nutz.doc.zdoc.ZDocScanningTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({	ColorTest.class,
						BlockMakerTest.class,
						ZDocParserTest.class,
						ZDocScanningTest.class,
						VarContextParserTest.class,
						WhitespaceAndTabTest.class,
						MetaScanningTest.class,
						HtmlDocRenderTest.class})
public class ZDocTestAll {}
