package net.mandomc.gameplay.tutorial;

import java.util.ArrayList;
import java.util.List;

/** Vanilla 26.1.2 ASCII advances, measured from the local client font bitmap. No artwork is bundled.
 * The normal written-book page is 114 pixels wide and fourteen nine-pixel text lines high.
 */
public final class BookPageLayout {
    public static final int WIDTH = 114;
    public static final int LINES = 14;
    private static final int[] ADVANCES = {4,2,4,6,6,6,6,2,4,4,4,6,2,6,2,6,6,6,6,6,6,6,6,6,6,6,2,2,5,6,5,6,7,6,6,6,6,6,6,6,6,4,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,4,6,4,6,6,3,6,6,6,6,6,5,6,6,2,6,5,3,6,6,6,6,6,6,6,4,6,6,6,6,6,6,4,2,4,7};
    private BookPageLayout() {}
    public static int width(String text, boolean bold) {
        int pixels=0;
        for(char c:text.toCharArray()) {
            if(c<32||c>126)throw new IllegalArgumentException("Handbook pages require printable ASCII text");
            pixels+=ADVANCES[c-32]+(bold&&c!=' '?1:0);
        }
        return pixels;
    }
    public static List<String> wrap(String text,boolean bold) {
        var lines=new ArrayList<String>();
        for(String paragraph:text.strip().split("\n",-1)) {
            if(paragraph.isBlank()){lines.add("");continue;}
            var line=new StringBuilder();
            for(String word:paragraph.strip().split(" +")) {
                if(width(word,bold)>WIDTH)throw new IllegalArgumentException("Handbook word is wider than a page: "+word);
                String candidate=line.isEmpty()?word:line+" "+word;
                if(width(candidate,bold)>WIDTH){lines.add(line.toString());line.setLength(0);}
                if(!line.isEmpty())line.append(' ');
                line.append(word);
            }
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }
    public static void validate(String heading,String body) {
        int lines=wrap(heading,true).size()+1+wrap(body,false).size();
        if(lines>LINES)throw new IllegalArgumentException("Handbook page '"+heading+"' needs "+lines+" lines; maximum "+LINES);
    }
}
