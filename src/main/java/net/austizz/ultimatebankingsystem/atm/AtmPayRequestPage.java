package net.austizz.ultimatebankingsystem.atm;

import java.util.List;
import java.util.UUID;

/** Compact display data shared by the actor and observers; never contains authentication data. */
public record AtmPayRequestPage(int page, int pages, List<Entry> entries) {
    public record Entry(UUID id, String name, String amount, String created) {
        public Entry { name=clean(name,80); amount=clean(amount,24); created=clean(created,40); }
    }
    public AtmPayRequestPage { pages=Math.max(1,pages); page=Math.clamp(page,0,pages-1); entries=List.copyOf(entries); }
    private static String clean(String value,int max) {
        value=value==null?"":value.replace('\n',' ').replace('\r',' ').replace('\t',' ');
        return value.substring(0,Math.min(max,value.length()));
    }
    public String encode() {
        var text=new StringBuilder().append(page).append('\t').append(pages);
        for(var row:entries) text.append('\n').append(row.id()).append('\t').append(row.name()).append('\t').append(row.amount()).append('\t').append(row.created());
        return text.toString();
    }
    public static AtmPayRequestPage decode(String text) {
        try {
            String[] lines=text.split("\n",5), heading=lines[0].split("\t");
            var rows=new java.util.ArrayList<Entry>();
            for(int i=1;i<lines.length;i++) {
                String[] row=lines[i].split("\t",-1);
                if(row.length!=4) throw new IllegalArgumentException();
                rows.add(new Entry(UUID.fromString(row[0]),row[1],row[2],row[3]));
            }
            return new AtmPayRequestPage(Integer.parseInt(heading[0]),Integer.parseInt(heading[1]),rows);
        } catch(RuntimeException malformed) { return new AtmPayRequestPage(0,1,List.of()); }
    }
}
