package com.ooaf.eventticketing.service;

import org.springframework.web.util.HtmlUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** A self-contained printable document; receives anonymous references and geometry only. */
public final class AnonymousSeatingPlanRenderer {
    private AnonymousSeatingPlanRenderer() {}
    public record Seat(String code, double x, double y, boolean accessible, boolean unavailable) {}
    public record Element(String key, String label, double x, double y, int rotation, List<Seat> seats) {}
    public record Assignment(String bookingReference, String groupReference, String seatCode, String splitReason) {}
    public record Plan(String title, LocalDateTime date, String venue, LocalDateTime completedAt,
            int version, boolean generalAdmission, boolean campusDemo,
            List<Element> elements, List<Assignment> assignments) {}
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", Locale.ENGLISH);

    public static String render(Plan plan) {
        var html = new StringBuilder("""
                <!doctype html><html lang="en"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Anonymous seating plan</title><style>
                body{font:14px/1.4 sans-serif;color:#111;background:white;max-width:1100px;margin:24px auto;padding:0 16px}
                h1{font-size:24px}h2{font-size:18px}h3{font-size:15px}svg{width:100%;height:auto;display:block}
                figure{margin:20px 0}table{width:100%;border-collapse:collapse;font-size:12px}
                th,td{border:1px solid #777;padding:6px;text-align:left;vertical-align:top;overflow-wrap:anywhere}
                caption{text-align:left;font-weight:bold;margin:16px 0 8px}.details{break-inside:avoid}
                @media print{@page{size:A4;margin:12mm}body{margin:0;padding:0;font-size:10pt;max-width:none}
                .instructions{display:none}header,figure{break-inside:avoid}thead{display:table-header-group}
                h2,h3{break-after:avoid}figure svg{max-height:160mm}tr{break-inside:avoid}table{font-size:9pt}th,td{padding:1.5mm}}
                </style></head><body><header><p>ANONYMOUS SEATING PLAN</p><h1>
                """);
        html.append(escape(plan.title())).append("</h1><p>").append(DATE.format(plan.date()))
                .append(" · ").append(escape(plan.venue())).append("</p><p>")
                .append(plan.assignments().size()).append(" confirmed bookings · ")
                .append(plan.generalAdmission() ? "General admission" : "Layout version " + plan.version())
                .append(" · Allocation completed ").append(DATE.format(plan.completedAt())).append("</p></header>")
                .append("<p class=\"instructions\">Use your browser's Print command to print or save as PDF.</p>")
                .append("<p>Only anonymous booking and group references are included. Group references are local to this document and cannot be used to join a group.</p>");
        Map<String, String> references = new HashMap<>();
        for (int i = 0; i < plan.assignments().size(); i++) {
            var assignment = plan.assignments().get(i);
            if (assignment.seatCode() != null) references.put(assignment.seatCode(), "P" + (i + 1));
        }
        if (plan.generalAdmission()) {
            html.append("<p>General admission: no seats are assigned.</p>");
        } else {
            if (plan.campusDemo()) html.append("<p>Campus demo: not a verified physical room map.</p>");
            html.append("<h2>Seating overview</h2>");
            diagram(html, plan.elements(), references);
            html.append("<p>Filled circle: assigned; open circle: unassigned; ×: unavailable; thick outline: accessible position. Gaps preserve aisles. P references match the table. Schematic, not to scale.</p>");
            // Large venues also get readable row/table details instead of relying on a scaled overview.
            if (plan.elements().stream().mapToInt(e -> e.seats().size()).sum() > 60) {
                html.append("<h2>Row and table details</h2>");
                for (var element : plan.elements()) {
                    if (element.seats().isEmpty()) continue;
                    html.append("<section class=\"details\"><h3>").append(escape(element.label())).append("</h3>");
                    diagram(html, List.of(new Element(element.key(), "", element.x(), element.y(),
                            element.rotation(), element.seats())), references);
                    html.append("</section>");
                }
            }
        }
        html.append("<table><caption>Anonymous assignment references</caption><thead><tr>")
                .append("<th scope=\"col\">Plan ref</th><th scope=\"col\">Booking reference</th><th scope=\"col\">Group</th><th scope=\"col\">Seat</th><th scope=\"col\">Split explanation</th></tr></thead><tbody>");
        for (int i = 0; i < plan.assignments().size(); i++) {
            var a = plan.assignments().get(i);
            html.append("<tr><td>P").append(i + 1).append("</td><td>").append(escape(a.bookingReference()))
                    .append("</td><td>").append(escape(a.groupReference() == null ? "Solo" : a.groupReference()))
                    .append("</td><td>").append(escape(a.seatCode() == null ? "General admission" : a.seatCode()))
                    .append("</td><td>").append(escape(a.splitReason())).append("</td></tr>");
        }
        return html.append("</tbody></table></body></html>").toString();
    }

    private static void diagram(StringBuilder html, List<Element> elements, Map<String, String> references) {
        var xs = new ArrayList<Double>(); var ys = new ArrayList<Double>();
        for (var element : elements) {
            if (!element.label().isEmpty()) { xs.add(element.x() * 3); ys.add(element.y() * 3); }
            for (var seat : element.seats()) { xs.add(seat.x() * 3); ys.add(seat.y() * 3); }
        }
        double left = xs.stream().mapToDouble(Double::doubleValue).min().orElse(0) - 40;
        double top = ys.stream().mapToDouble(Double::doubleValue).min().orElse(0) - 40;
        double width = xs.stream().mapToDouble(Double::doubleValue).max().orElse(0) - left + 40;
        double height = ys.stream().mapToDouble(Double::doubleValue).max().orElse(0) - top + 40;
        html.append("<figure><svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\"Anonymous seating diagram; text references are listed below\" viewBox=\"")
                .append(left).append(' ').append(top).append(' ').append(width).append(' ').append(height).append("\" style=\"max-width:").append(width).append("px\">");
        for (var element : elements) {
            if (!element.label().isEmpty()) html.append("<text x=\"").append(element.x() * 3).append("\" y=\"")
                    .append(element.y() * 3 - 24).append("\" font-size=\"12\" transform=\"rotate(")
                    .append(element.rotation()).append(' ').append(element.x() * 3).append(' ').append(element.y() * 3 - 24)
                    .append(")\">").append(escape(element.label())).append("</text>");
            for (var seat : element.seats()) {
                String reference = references.get(seat.code());
                html.append("<g transform=\"translate(").append(seat.x() * 3).append(' ').append(seat.y() * 3).append(")\"><title>")
                        .append(escape(seat.code())).append(seat.accessible() ? " · Accessible" : "")
                        .append(seat.unavailable() ? " · Unavailable" : "").append("</title><circle r=\"8\" fill=\"white\" stroke=\"black\" stroke-width=\"")
                        .append(seat.accessible() ? 3 : 1).append("\"/><text text-anchor=\"middle\" y=\"4\" font-size=\"10\">")
                        .append(reference != null ? "●" : seat.unavailable() ? "×" : "○").append("</text>")
                        .append("<text text-anchor=\"middle\" y=\"22\" font-size=\"11\">").append(escape(seat.code())).append("</text>");
                if (reference != null) html.append("<text text-anchor=\"middle\" y=\"-15\" font-size=\"11\">").append(reference).append("</text>");
                html.append("</g>");
            }
        }
        html.append("</svg></figure>");
    }

    private static String escape(String value) { return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8"); }
}
