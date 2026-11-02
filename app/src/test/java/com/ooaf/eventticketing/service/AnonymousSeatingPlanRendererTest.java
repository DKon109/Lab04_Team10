package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.service.AnonymousSeatingPlanRenderer.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnonymousSeatingPlanRendererTest {
    @Test void rendersLargeVenueWithReadableDetailsAndCompleteReferences() throws Exception {
        var rows = new ArrayList<Element>(); var assignments = new ArrayList<Assignment>();
        for (int row = 0; row < 15; row++) {
            var seats = new ArrayList<Seat>();
            for (int col = 1; col <= 20; col++) {
                String code = (char) ('A' + row) + Integer.toString(col);
                seats.add(new Seat(code, (col - 1 + (col > 10 ? 1 : 0)) * 10, row * 24, col == 1, row == 14 && col == 20));
                if (row < 14) assignments.add(new Assignment("BKG-" + (row * 20 + col),
                        "G" + (row * 4 + (col - 1) / 5 + 1), code, null));
            }
            rows.add(new Element("row-" + row, "Row " + (char) ('A' + row), -12, row * 24, 0, seats));
        }
        rows.add(new Element("stage", "STAGE", 100, -20, 0, List.of()));
        String html = AnonymousSeatingPlanRenderer.render(new Plan("Synthetic 300-seat plan", LocalDateTime.of(2099, 1, 1, 18, 0),
                "Synthetic hall", LocalDateTime.of(2098, 12, 31, 12, 0), 2, false, false, rows, assignments));
        assertFalse(html.contains("Row and table details"), "Per-block details are replaced by the numbered overview");
        assertTrue(html.contains("<td>BKG-280</td>")); assertTrue(html.contains("O20 · Unavailable"));
        assertTrue(html.contains("A1 · Accessible · G1"));
        assertTrue(html.contains("text-anchor=\"middle\">20</text>"), "Seat boxes show seat numbers");
        assertEquals(2, html.split("font-weight=\"700\" fill=\"#526159\" text-anchor=\"middle\">O</text>", -1).length - 1,
                "Row labels appear at both ends of each row");
        assertTrue(html.contains(">STAGE</text>"));
        assertNotEquals(fillOf(html, "A5"), fillOf(html, "A6"), "Neighbouring groups need different colours");
        assertEquals(fillOf(html, "A1"), fillOf(html, "A5"), "One group keeps one colour");
        assertFalse(html.contains("<script"));
        Path directory = Path.of("target", "plan-qa"); Files.createDirectories(directory);
        Files.writeString(directory.resolve("backend-large.html"), html);
    }

    @Test void marksSplitGroupsAndAddsStageWhenLayoutHasNone() {
        var seats = new ArrayList<Seat>();
        for (int number = 1; number <= 6; number++) seats.add(new Seat("B" + number, "B", Integer.toString(number),
                number * 20, 40, false, false));
        var assignments = List.of(new Assignment("BKG-1", "G1", "B1", null),
                new Assignment("BKG-2", "G1", "B5", "Row capacity forced a split"),
                new Assignment("BKG-3", null, "B3", null));
        var date = LocalDateTime.of(2099, 1, 1, 12, 0);
        String html = AnonymousSeatingPlanRenderer.render(new Plan("Split", date, "Hall", date, 1, false, false,
                List.of(new Element("row-b", "B - Centre", 60, 40, 0, seats)), assignments));
        assertTrue(html.contains("stroke-dasharray"), "Split seats get a dashed outline");
        assertTrue(html.contains(">STAGE</text>"), "The front row faces a stage even when none is mapped");
        assertTrue(html.contains("B3 · Solo")); assertTrue(html.contains("B2 · Empty"));
        assertTrue(html.contains("<small>SPLIT GROUPS</small><strong>1</strong>"));
    }

    @Test void followsAngledBlocksAndWrapsStageInsideFrontRow() {
        // Thrust layout: front row A has a straight centre block plus diagonal and vertical side blocks.
        var centre = new ArrayList<Seat>(); var diagonal = new ArrayList<Seat>(); var column = new ArrayList<Seat>();
        for (int n = 1; n <= 5; n++) centre.add(new Seat("A" + (10 + n), "A", Integer.toString(10 + n), 100 + n * 20, 0, false, false));
        for (int n = 1; n <= 5; n++) diagonal.add(new Seat("A" + (20 + n), "A", Integer.toString(20 + n), 230 + n * 14, 14 + n * 14, false, false));
        for (int n = 1; n <= 5; n++) column.add(new Seat("A" + n, "A", Integer.toString(n), 330, 200 - n * 20, false, false));
        var date = LocalDateTime.of(2099, 1, 1, 12, 0);
        String html = AnonymousSeatingPlanRenderer.render(new Plan("Thrust", date, "Hall", date, 1, false, false,
                List.of(new Element("c", "A - Centre", 120, 0, 0, centre), new Element("d", "A - Diagonal", 244, 28, 0, diagonal),
                        new Element("v", "A - Column", 330, 180, 0, column)),
                List.of(new Assignment("BKG-1", "G1", "A21", null))));
        assertTrue(html.contains("transform=\"rotate(45.0)\""), "Diagonal rows turn their seat boxes");
        assertTrue(html.contains("transform=\"rotate(90.0)\""), "Vertical rows turn their seat boxes");
        String points = html.substring(html.indexOf("<polygon points=\"") + 16, html.indexOf("\" fill=\"#dce6cf\""));
        assertEquals(6, points.trim().split(" ").length, "A front row that wraps the stage gets an octagonal stage");
        assertEquals(6, html.split("font-weight=\"700\" fill=\"#526159\" text-anchor=\"middle\">A</text>", -1).length - 1,
                "Each block of row A is labelled at both ends");
    }

    private static String fillOf(String html, String code) {
        int seat = html.indexOf("<title>" + code + " ");
        int fill = html.indexOf("fill=\"", html.indexOf("<rect", seat + 1)) + 6;
        while (html.startsWith("none", fill)) fill = html.indexOf("fill=\"", fill) + 6;
        return html.substring(fill, html.indexOf('"', fill));
    }

    @Test void escapesEveryTextFieldIncludingSvgLabelsAndReferences() {
        var seat = new Seat("A<1>", 0, 0, false, false);
        var element = new Element("row", "<img src=x onerror=alert(1)>", 0, 0, 0, List.of(seat));
        var assignment = new Assignment("B<1>", "G&1", "A<1>", "<script>alert(2)</script>");
        var date = LocalDateTime.of(2099, 1, 1, 12, 0);
        String html = AnonymousSeatingPlanRenderer.render(new Plan("<script>x</script>", date, "<iframe>", date, 1,
                false, false, List.of(element), List.of(assignment)));
        assertFalse(html.contains("<script>")); assertFalse(html.contains("<img")); assertFalse(html.contains("<iframe>"));
        assertTrue(html.contains("A&lt;1&gt;")); assertTrue(html.contains("B&lt;1&gt;")); assertTrue(html.contains("G&amp;1"));
    }
}
