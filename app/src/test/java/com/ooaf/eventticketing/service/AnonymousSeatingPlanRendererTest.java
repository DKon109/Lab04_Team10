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
        assertTrue(html.contains("Row and table details")); assertTrue(html.contains("<td>P280</td>"));
        assertTrue(html.contains("BKG-280")); assertTrue(html.contains("O20 · Unavailable"));
        assertFalse(html.contains("<script"));
        Path directory = Path.of("target", "plan-qa"); Files.createDirectories(directory);
        Files.writeString(directory.resolve("backend-large.html"), html);
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
