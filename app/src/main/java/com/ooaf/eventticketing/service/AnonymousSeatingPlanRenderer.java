package com.ooaf.eventticketing.service;

import org.springframework.web.util.HtmlUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * A self-contained printable document; receives anonymous references and geometry only.
 * Seats are drawn as numbered boxes filled with their group's colour, with row letters at both
 * ends of every row. Colours and typography follow the attendee seat map in the frontend.
 */
public final class AnonymousSeatingPlanRenderer {
    private AnonymousSeatingPlanRenderer() {}
    public record Seat(String code, String rowLabel, String number, double x, double y,
            boolean accessible, boolean unavailable) {
        /** Derives the row label and number from codes such as "A12" when they are not supplied. */
        public Seat(String code, double x, double y, boolean accessible, boolean unavailable) {
            this(code, rowOf(code), numberOf(code), x, y, accessible, unavailable);
        }
    }
    public record Element(String key, String label, double x, double y, int rotation, List<Seat> seats) {}
    public record Assignment(String bookingReference, String groupReference, String seatCode, String splitReason) {}
    public record Plan(String title, LocalDateTime date, String venue, LocalDateTime completedAt,
            int version, boolean generalAdmission, boolean campusDemo,
            List<Element> elements, List<Assignment> assignments) {}
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", Locale.ENGLISH);
    private static final double SCALE = 3;
    // Soft fills keep dark seat numbers readable on screen and in print.
    private static final String[] GROUP_COLOURS = {"#fdd36a", "#8fd3c2", "#f4a3a8", "#9cc3f0", "#c9b3ec",
            "#b8e07a", "#f8b97a", "#f2a8d8", "#7fd0e8", "#e3d27a", "#a9b8f5", "#d6f26a"};
    private static final String INK = "#142c24", INK_SOFT = "#526159", LINE = "#dfe5db";
    private static final String SOLO_COLOUR = "#cbd5c0";
    private static final String EMPTY_FILL = "#ffffff", EMPTY_STROKE = "#b5c3a3";
    private static final String BLOCKED_FILL = "#f1e5e1", BLOCKED_STROKE = "#d5bcb1", BLOCKED_INK = "#994432";
    private static final String ACCESSIBLE = "#466ea3", STAGE = "#dce6cf";

    /** Distance between neighbouring seats in a row, in plan units. */
    private record Spacing(double pitch) {}
    /** One printed row: a block sharing a row label; horizontal blocks of the same row are merged. */
    private record Line(String label, boolean rowLike, List<Seat> seats) {
        /** Unit vector from the first to the last seat; rows with one seat read left to right. */
        double[] direction() {
            var first = seats.get(0); var last = seats.get(seats.size() - 1);
            double dx = last.x() - first.x(), dy = last.y() - first.y(), length = Math.hypot(dx, dy);
            return length < 0.001 ? new double[] {1, 0} : new double[] {dx / length, dy / length};
        }
        /** Row angle in degrees, folded so boxes and labels never appear upside down. */
        double angle() {
            var d = direction();
            double degrees = Math.toDegrees(Math.atan2(d[1], d[0]));
            if (degrees > 90) degrees -= 180;
            if (degrees <= -90) degrees += 180;
            return Math.abs(degrees) < 0.01 ? 0 : degrees;
        }
    }

    public static String render(Plan plan) {
        var html = new StringBuilder("""
                <!doctype html><html lang="en"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Anonymous seating plan</title><style>
                *{box-sizing:border-box;-webkit-print-color-adjust:exact;print-color-adjust:exact}
                body{font:14px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;
                color:#142c24;background:#f6f7f2;max-width:1280px;margin:24px auto;padding:0 16px}
                .eyebrow{font-size:11px;letter-spacing:.14em;color:#526159;margin:0}
                h1{font-size:26px;letter-spacing:-.02em;margin:4px 0}h2{font-size:17px;margin:28px 0 10px}
                header p{color:#526159;margin:4px 0}.panel{background:#fff;border:1px solid #dfe5db;border-radius:16px;padding:16px;margin:14px 0}
                .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px;margin:16px 0}
                .stats div{background:#fff;border:1px solid #dfe5db;border-radius:12px;padding:10px 12px}
                .stats small{display:block;font-size:10px;letter-spacing:.1em;color:#526159}.stats strong{font-size:20px}
                svg{height:auto;display:block;max-width:none}figure{margin:0;overflow-x:auto}
                .legend{display:flex;flex-wrap:wrap;gap:6px 18px;font-size:12px;color:#526159;margin:12px 0 0}
                .key{display:inline-block;width:16px;height:16px;border-radius:4px 4px 3px 3px;vertical-align:-3px;margin-right:6px;border:1px solid #b5c3a3}
                table{width:100%;border-collapse:collapse;font-size:12px;background:#fff}
                th,td{border:1px solid #dfe5db;padding:6px 8px;text-align:left;vertical-align:top;overflow-wrap:anywhere}
                th{background:#f0f7d7}caption{text-align:left;font-weight:700;margin:24px 0 8px}
                @media print{@page{size:A4;margin:12mm}body{margin:0;padding:0;font-size:10pt;max-width:none;background:#fff}
                .instructions{display:none}header,figure,.panel{break-inside:avoid}thead{display:table-header-group}
                h2{break-after:avoid}figure{overflow:visible}figure svg{width:100%!important;max-height:170mm}tr{break-inside:avoid}table{font-size:9pt}th,td{padding:1.5mm}}
                </style></head><body><header><p class="eyebrow">ANONYMOUS SEATING PLAN</p><h1>
                """);
        html.append(escape(plan.title())).append("</h1><p>").append(DATE.format(plan.date()))
                .append(" · ").append(escape(plan.venue())).append("</p><p>")
                .append(plan.assignments().size()).append(" confirmed bookings · ")
                .append(plan.generalAdmission() ? "General admission" : "Layout version " + plan.version())
                .append(" · Allocation completed ").append(DATE.format(plan.completedAt())).append("</p></header>")
                .append("<p class=\"instructions\">Use your browser's Print command to print or save as PDF.</p>")
                .append("<p>Only anonymous booking and group references are included. Group references are local to this document and cannot be used to join a group.</p>");
        Map<String, Assignment> bySeat = new HashMap<>();
        for (var assignment : plan.assignments()) {
            if (assignment.seatCode() != null) bySeat.put(assignment.seatCode(), assignment);
        }
        var spacing = spacing(plan.elements());
        var lines = lines(plan.elements());
        var colours = groupColours(plan, bySeat, spacing);
        if (plan.generalAdmission()) {
            html.append("<p>General admission: no seats are assigned.</p>");
        } else {
            if (plan.campusDemo()) html.append("<p>Campus demo: not a verified physical room map.</p>");
            stats(html, plan, bySeat, colours);
            html.append("<h2>Seating overview</h2><div class=\"panel\">");
            diagram(html, plan.elements(), lines, bySeat, colours, spacing);
            legend(html);
            html.append("</div>");
        }
        html.append("<table><caption>Anonymous assignment references</caption><thead><tr>")
                .append("<th scope=\"col\">Colour</th><th scope=\"col\">Booking reference</th><th scope=\"col\">Group</th><th scope=\"col\">Seat</th><th scope=\"col\">Split explanation</th></tr></thead><tbody>");
        for (var a : plan.assignments()) {
            html.append("<tr><td><span class=\"key\" style=\"background:").append(colourOf(a, colours))
                    .append("\"></span></td><td>").append(escape(a.bookingReference()))
                    .append("</td><td>").append(escape(a.groupReference() == null ? "Solo" : a.groupReference()))
                    .append("</td><td>").append(escape(a.seatCode() == null ? "General admission" : a.seatCode()))
                    .append("</td><td>").append(escape(a.splitReason())).append("</td></tr>");
        }
        return html.append("</tbody></table></body></html>").toString();
    }

    private static void stats(StringBuilder html, Plan plan, Map<String, Assignment> bySeat, Map<String, String> colours) {
        int seats = 0, blocked = 0;
        for (var element : plan.elements()) for (var seat : element.seats()) {
            seats++;
            if (seat.unavailable() && !bySeat.containsKey(seat.code())) blocked++;
        }
        long split = plan.assignments().stream().filter(a -> isSplit(a) && a.groupReference() != null)
                .map(Assignment::groupReference).distinct().count();
        html.append("<div class=\"stats\">");
        stat(html, "ASSIGNED", bySeat.size()); stat(html, "EMPTY", seats - bySeat.size() - blocked);
        stat(html, "UNAVAILABLE", blocked); stat(html, "GROUPS", colours.size()); stat(html, "SPLIT GROUPS", split);
        html.append("</div>");
    }

    private static void stat(StringBuilder html, String label, long value) {
        html.append("<div><small>").append(label).append("</small><strong>").append(value).append("</strong></div>");
    }

    private static void legend(StringBuilder html) {
        html.append("<div class=\"legend\"><div><span class=\"key\" style=\"background:").append(GROUP_COLOURS[0])
                .append("\"></span><span class=\"key\" style=\"background:").append(GROUP_COLOURS[1])
                .append("\"></span>Assigned · one colour per group</div><div><span class=\"key\" style=\"background:")
                .append(SOLO_COLOUR).append("\"></span>Solo booking</div><div><span class=\"key\"></span>Empty seat</div>")
                .append("<div><span class=\"key\" style=\"background:").append(BLOCKED_FILL).append(";border-color:")
                .append(BLOCKED_STROKE).append("\"></span>Unavailable</div><div><span class=\"key\" style=\"outline:2px solid ")
                .append(ACCESSIBLE).append(";outline-offset:1px\"></span>Accessible position</div>")
                .append("<div><span class=\"key\" style=\"border:2px dashed ").append(INK).append("\"></span>Group split</div></div>")
                .append("<p class=\"legend\">Numbers are seat numbers; letters at both ends of a row are row labels. Neighbouring groups always get different colours. Gaps preserve aisles. Positions follow the venue layout; not to scale.</p>");
    }

    private static void diagram(StringBuilder html, List<Element> elements, List<Line> lines, Map<String, Assignment> bySeat,
            Map<String, String> colours, Spacing spacing) {
        double pitch = spacing.pitch() * SCALE, size = pitch * 0.84;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean hasStage = false;
        for (var element : elements) {
            if (element.seats().isEmpty()) {
                hasStage = true;
                minX = Math.min(minX, element.x() * SCALE - pitch * 4); maxX = Math.max(maxX, element.x() * SCALE + pitch * 4);
                minY = Math.min(minY, element.y() * SCALE); maxY = Math.max(maxY, element.y() * SCALE);
            }
            for (var seat : element.seats()) {
                minX = Math.min(minX, seat.x() * SCALE); maxX = Math.max(maxX, seat.x() * SCALE);
                minY = Math.min(minY, seat.y() * SCALE); maxY = Math.max(maxY, seat.y() * SCALE);
            }
        }
        if (minX == Double.MAX_VALUE) { minX = maxX = minY = maxY = 0; }
        double[][] stage = hasStage ? null : stageOutline(lines, pitch, size, minX, maxX, minY, maxY);
        if (stage != null) for (var point : stage) {
            minX = Math.min(minX, point[0]); maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]); maxY = Math.max(maxY, point[1]);
        }
        double margin = size * 2.2;
        double left = minX - margin, top = minY - margin, width = maxX - left + margin, height = maxY - top + margin;
        html.append("<figure><svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\"Anonymous seating diagram; seat references are listed below\" viewBox=\"")
                .append(left).append(' ').append(top).append(' ').append(width).append(' ').append(height)
                .append("\" style=\"width:max(100%,").append(Math.round(width / size * 14)).append("px)\" font-family=\"-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif\">");
        if (stage != null) stage(html, stage, size, "STAGE");
        for (var element : elements) {
            if (!element.seats().isEmpty()) continue;
            double cx = element.x() * SCALE, cy = element.y() * SCALE, half = pitch * 4;
            stage(html, new double[][] {{cx - half, cy - size * 0.6}, {cx + half, cy - size * 0.6},
                    {cx + half, cy + size * 0.6}, {cx - half, cy + size * 0.6}}, size, element.label());
        }
        Map<String, Double> rotation = new HashMap<>();
        for (var line : lines) {
            var first = line.seats().get(0); var last = line.seats().get(line.seats().size() - 1);
            if (line.rowLike()) {
                double angle = line.angle();
                line.seats().forEach(seat -> rotation.put(seat.code(), angle));
                var d = line.direction();
                rowLabel(html, first.x() * SCALE - d[0] * size * 1.15, first.y() * SCALE - d[1] * size * 1.15, size, line.label());
                rowLabel(html, last.x() * SCALE + d[0] * size * 1.15, last.y() * SCALE + d[1] * size * 1.15, size, line.label());
            } else {
                double centre = line.seats().stream().mapToDouble(Seat::x).average().orElse(0) * SCALE;
                html.append("<text x=\"").append(centre).append("\" y=\"").append(minYOf(line) * SCALE - size * 0.9)
                        .append("\" font-size=\"").append(size * 0.45).append("\" fill=\"").append(INK_SOFT)
                        .append("\" text-anchor=\"middle\">").append(escape(line.label())).append("</text>");
            }
        }
        for (var element : elements) for (var seat : element.seats()) {
            var assignment = bySeat.get(seat.code());
            boolean split = isSplit(assignment);
            String fill = assignment != null ? colourOf(assignment, colours) : seat.unavailable() ? BLOCKED_FILL : EMPTY_FILL;
            String stroke = split ? INK : assignment != null ? "#7d8b70" : seat.unavailable() ? BLOCKED_STROKE : EMPTY_STROKE;
            String ink = assignment == null && seat.unavailable() ? BLOCKED_INK : INK;
            double half = size / 2, angle = rotation.getOrDefault(seat.code(), 0.0);
            String turn = angle == 0 ? "" : " transform=\"rotate(" + Math.round(angle * 10) / 10.0 + ")\"";
            html.append("<g transform=\"translate(").append(seat.x() * SCALE).append(' ').append(seat.y() * SCALE)
                    .append(")\"><title>").append(seatTitle(seat, assignment)).append("</title>");
            if (seat.accessible()) html.append("<rect x=\"").append(-half - size * 0.12).append("\" y=\"").append(-half - size * 0.12)
                    .append("\" width=\"").append(size * 1.24).append("\" height=\"").append(size * 1.24).append("\" rx=\"")
                    .append(size * 0.24).append("\" fill=\"none\" stroke=\"").append(ACCESSIBLE).append("\" stroke-width=\"")
                    .append(size * 0.08).append('"').append(turn).append("/>");
            html.append("<rect x=\"").append(-half).append("\" y=\"").append(-half).append("\" width=\"").append(size)
                    .append("\" height=\"").append(size).append("\" rx=\"").append(size * 0.16).append("\" fill=\"").append(fill)
                    .append("\" stroke=\"").append(stroke).append("\" stroke-width=\"").append(size * (split ? 0.09 : 0.045)).append('"');
            if (split) html.append(" stroke-dasharray=\"").append(size * 0.18).append(' ').append(size * 0.1).append('"');
            html.append(turn).append("/><text y=\"").append(size * 0.17).append("\" font-size=\"").append(size * fontScale(seat.number()))
                    .append("\" font-weight=\"600\" fill=\"").append(ink).append("\" text-anchor=\"middle\">")
                    .append(escape(seat.number())).append("</text></g>");
        }
        html.append("</svg></figure>");
    }

    /**
     * Places the stage beside the front row (the first row label). A straight front row gets a bar on
     * the side away from the other seats; a front row that wraps around the stage (as in a thrust
     * theatre) gets an octagon filling the space it encloses.
     */
    private static double[][] stageOutline(List<Line> lines, double pitch, double size,
            double minX, double maxX, double minY, double maxY) {
        var rows = lines.stream().filter(Line::rowLike).toList();
        double centreY = lines.stream().flatMap(line -> line.seats().stream()).mapToDouble(Seat::y).average().orElse(0) * SCALE;
        String front = rows.stream().map(Line::label)
                .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())).orElse(null);
        var frontSeats = rows.stream().filter(line -> line.label().equals(front)).flatMap(line -> line.seats().stream()).toList();
        double fMinX = frontSeats.stream().mapToDouble(Seat::x).min().orElse(minX / SCALE) * SCALE;
        double fMaxX = frontSeats.stream().mapToDouble(Seat::x).max().orElse(maxX / SCALE) * SCALE;
        double fMinY = frontSeats.stream().mapToDouble(Seat::y).min().orElse(minY / SCALE) * SCALE;
        double fMaxY = frontSeats.stream().mapToDouble(Seat::y).max().orElse(minY / SCALE) * SCALE;
        if (fMaxY - fMinY < pitch * 2) {
            double half = Math.max(maxX - minX, size * 6) * 0.3, cx = (minX + maxX) / 2;
            double y = (fMinY + fMaxY) / 2 <= centreY ? minY - size * 2.2 : maxY + size * 2.2;
            return new double[][] {{cx - half, y - size * 0.6}, {cx + half, y - size * 0.6},
                    {cx + half, y + size * 0.6}, {cx - half, y + size * 0.6}};
        }
        double x0 = fMinX + pitch * 1.2, x1 = fMaxX - pitch * 1.2, y0 = fMinY + pitch * 1.4, y1 = fMaxY + pitch * 0.6;
        double cut = Math.min((x1 - x0) / 3, (y1 - y0) * 0.35);
        return new double[][] {{x0 + cut, y0}, {x1 - cut, y0}, {x1, y0 + cut}, {x1, y1}, {x0, y1}, {x0, y0 + cut}};
    }

    private static void stage(StringBuilder html, double[][] outline, double size, String label) {
        double sumX = 0, sumY = 0;
        html.append("<polygon points=\"");
        for (var point : outline) {
            html.append(point[0]).append(',').append(point[1]).append(' ');
            sumX += point[0]; sumY += point[1];
        }
        double cx = sumX / outline.length, cy = sumY / outline.length;
        html.append("\" fill=\"").append(STAGE).append("\" stroke-linejoin=\"round\"/><text x=\"").append(cx).append("\" y=\"")
                .append(cy + size * 0.2).append("\" font-size=\"").append(size * 0.5).append("\" letter-spacing=\"").append(size * 0.12)
                .append("\" fill=\"").append(INK).append("\" text-anchor=\"middle\">").append(escape(label)).append("</text>");
    }

    private static void rowLabel(StringBuilder html, double x, double y, double size, String label) {
        html.append("<text x=\"").append(x).append("\" y=\"").append(y + size * 0.2).append("\" font-size=\"").append(size * 0.55)
                .append("\" font-weight=\"700\" fill=\"").append(INK_SOFT).append("\" text-anchor=\"middle\">")
                .append(escape(label)).append("</text>");
    }

    /**
     * One line per block. Horizontal blocks of the same row and height (e.g. left/centre/right) merge
     * into one printed row; angled or curved blocks keep their own line and seat order.
     */
    private static List<Line> lines(List<Element> elements) {
        var ordered = elements.stream().filter(e -> !e.seats().isEmpty())
                .sorted(Comparator.comparingDouble((Element e) -> e.seats().get(0).y())
                        .thenComparingDouble(e -> e.seats().stream().mapToDouble(Seat::x).min().orElse(0))).toList();
        var lines = new ArrayList<Line>();
        Map<String, Line> horizontalRows = new LinkedHashMap<>();
        for (var element : ordered) {
            var labels = new HashSet<String>(); var ys = new HashSet<Double>();
            element.seats().forEach(seat -> { labels.add(String.valueOf(seat.rowLabel())); ys.add(seat.y()); });
            boolean rowLike = labels.size() == 1 && !labels.contains("null") && !labels.contains("");
            if (!rowLike) {
                lines.add(new Line(element.label(), false, sortedByX(element.seats())));
            } else if (ys.size() == 1) {
                String key = labels.iterator().next() + "@" + ys.iterator().next();
                var existing = horizontalRows.get(key);
                if (existing == null) {
                    var line = new Line(labels.iterator().next(), true, new ArrayList<>(element.seats()));
                    horizontalRows.put(key, line); lines.add(line);
                } else {
                    existing.seats().addAll(element.seats());
                }
            } else {
                lines.add(new Line(labels.iterator().next(), true, List.copyOf(element.seats())));
            }
        }
        return lines.stream().map(line -> line.seats().stream().map(Seat::y).distinct().count() == 1
                ? new Line(line.label(), line.rowLike(), sortedByX(line.seats())) : line).toList();
    }

    /** Greedy colouring in seat order, so groups seated next to each other never share a colour. */
    private static Map<String, String> groupColours(Plan plan, Map<String, Assignment> bySeat, Spacing spacing) {
        var seated = new ArrayList<Seat>();
        for (var element : plan.elements()) for (var seat : element.seats()) {
            var assignment = bySeat.get(seat.code());
            if (assignment != null && assignment.groupReference() != null) seated.add(seat);
        }
        seated.sort(Comparator.comparingDouble(Seat::y).thenComparingDouble(Seat::x));
        double reach = spacing.pitch() * 2.6;
        Map<String, Set<String>> neighbours = new LinkedHashMap<>();
        for (var seat : seated) neighbours.computeIfAbsent(bySeat.get(seat.code()).groupReference(), g -> new HashSet<>());
        for (int i = 0; i < seated.size(); i++) {
            var seat = seated.get(i);
            String group = bySeat.get(seat.code()).groupReference();
            for (int j = i + 1; j < seated.size(); j++) {
                var other = seated.get(j);
                if (other.y() - seat.y() > reach) break;
                String otherGroup = bySeat.get(other.code()).groupReference();
                if (!group.equals(otherGroup) && Math.hypot(other.x() - seat.x(), other.y() - seat.y()) <= reach) {
                    neighbours.get(group).add(otherGroup); neighbours.get(otherGroup).add(group);
                }
            }
        }
        for (var assignment : plan.assignments()) {
            if (assignment.groupReference() != null) neighbours.computeIfAbsent(assignment.groupReference(), g -> new HashSet<>());
        }
        Map<String, String> colours = new LinkedHashMap<>();
        int order = 0;
        for (var entry : neighbours.entrySet()) {
            Set<String> used = new HashSet<>();
            for (var neighbour : entry.getValue()) if (colours.containsKey(neighbour)) used.add(colours.get(neighbour));
            String chosen = GROUP_COLOURS[order % GROUP_COLOURS.length];
            for (int k = 0; k < GROUP_COLOURS.length; k++) {
                String candidate = GROUP_COLOURS[(order + k) % GROUP_COLOURS.length];
                if (!used.contains(candidate)) { chosen = candidate; break; }
            }
            colours.put(entry.getKey(), chosen);
            order++;
        }
        return colours;
    }

    /** Smallest gap between consecutive seats of a block, measured along the row. */
    private static Spacing spacing(List<Element> elements) {
        double pitch = Double.MAX_VALUE;
        for (var element : elements) {
            var seats = element.seats();
            for (int i = 1; i < seats.size(); i++) {
                double gap = Math.hypot(seats.get(i).x() - seats.get(i - 1).x(), seats.get(i).y() - seats.get(i - 1).y());
                if (gap > 0.001) pitch = Math.min(pitch, gap);
            }
        }
        return new Spacing(pitch == Double.MAX_VALUE ? 10 : pitch);
    }

    private static List<Seat> sortedByX(List<Seat> seats) {
        return seats.stream().sorted(Comparator.comparingDouble(Seat::x)).toList();
    }

    private static double minYOf(Line line) {
        return line.seats().stream().mapToDouble(Seat::y).min().orElse(0);
    }

    private static double fontScale(String number) {
        int length = number == null ? 1 : number.length();
        return length <= 2 ? 0.5 : length == 3 ? 0.4 : 0.3;
    }

    private static boolean isSplit(Assignment assignment) {
        return assignment != null && assignment.splitReason() != null && !assignment.splitReason().isBlank();
    }

    private static String colourOf(Assignment assignment, Map<String, String> colours) {
        return assignment.groupReference() == null ? SOLO_COLOUR : colours.getOrDefault(assignment.groupReference(), SOLO_COLOUR);
    }

    private static String seatTitle(Seat seat, Assignment assignment) {
        return escape(seat.code()) + (seat.accessible() ? " · Accessible" : "") + (seat.unavailable() ? " · Unavailable" : "")
                + (assignment == null ? (seat.unavailable() ? "" : " · Empty")
                : " · " + escape(assignment.groupReference() == null ? "Solo" : assignment.groupReference()));
    }

    private static String rowOf(String code) {
        if (code == null) return "";
        int end = 0;
        while (end < code.length() && Character.isLetter(code.charAt(end))) end++;
        return end > 0 && end < code.length() ? code.substring(0, end) : "";
    }

    private static String numberOf(String code) {
        if (code == null) return "";
        String row = rowOf(code);
        return row.isEmpty() ? code : code.substring(row.length());
    }

    private static String escape(String value) { return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8"); }
}
