package com.finn.bump.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.finn.bump.R
import com.finn.bump.data.Appointment
import com.finn.bump.data.AppState
import com.finn.bump.data.Chapter
import com.finn.bump.data.ChecklistItem
import com.finn.bump.data.Contact
import com.finn.bump.data.Contraction
import com.finn.bump.data.KickSession
import com.finn.bump.data.Moment
import com.finn.bump.data.Note
import com.finn.bump.data.Photo
import com.finn.bump.data.PhotoFiles
import com.finn.bump.data.WeightEntry
import com.finn.bump.data.displayName
import com.finn.bump.data.keepsakeChapters
import com.finn.bump.data.measuredOn
import com.finn.bump.data.withoutTombstones
import com.finn.bump.domain.Contractions
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.WeekTable
import com.finn.bump.ui.components.weekMark
import com.finn.bump.ui.theme.Palette
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The record as a small printed book: a cover of every week's mark, a page of the facts,
 * then the pregnancy week by week with everything that was written down in it.
 *
 * Drawn with the platform's own [PdfDocument] and the app's own fonts, palette and marks,
 * so it needs no PDF library and looks like the app it came from. A5, because it is a
 * keepsake to hold rather than a report to file.
 *
 * Always the light palette, whatever the phone's theme: it is paper.
 */
class KeepsakePdf(context: Context, private val weeks: WeekTable, private val photos: PhotoFiles) {

    private val ctx: Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
    )

    private val serif: Typeface = ResourcesCompat.getFont(ctx, R.font.gambetta_500)!!
    private val sans: Typeface = ResourcesCompat.getFont(ctx, R.font.satoshi_400)!!
    private val sansMedium: Typeface = ResourcesCompat.getFont(ctx, R.font.satoshi_500)!!
    private val sansBold: Typeface = ResourcesCompat.getFont(ctx, R.font.satoshi_700)!!

    private val paper = Palette.PaperLight.toArgb()
    private val ink = Palette.InkLight.toArgb()
    private val muted = Palette.MutedLight.toArgb()
    private val label = Palette.LabelLight.toArgb()
    private val rule = Palette.RuleLight.toArgb()
    private val clay = Palette.Clay.toArgb()

    /** Photos that could not be placed in the last book written; see [write]. */
    private var unplaced = 0

    /**
     * Draw the book into [out]. Returns how many photos could not be placed — bytes not
     * on this phone yet, or a file that would not open — so the caller can say so rather
     * than report a complete book.
     */
    suspend fun write(state: AppState, out: OutputStream): Int {
        val due = requireNotNull(state.dueDate) { "No due date to make a keepsake from" }
        unplaced = 0
        val doc = PdfDocument()
        val pages = Pages(doc)
        try {
            cover(pages, state, due)
            facts(pages, state, due)
            val chapters = keepsakeChapters(state)
            chapters.filter { it.week != null }.forEach { chapter(pages, it, due) }
            state.bornOn?.let { birth(pages, due, it) }
            chapters.firstOrNull { it.week == null }?.let { chapter(pages, it, due) }
            colophon(pages)
            pages.finish()
            doc.writeTo(out)
            return unplaced
        } finally {
            // A page left open by a failure would make close() throw and bury the real error.
            pages.finish()
            doc.close()
        }
    }

    // ---- Pages ------------------------------------------------------------------------

    private inner class Pages(private val doc: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var number = 0
        var y = 0f

        val canvas: Canvas get() = page!!.canvas

        fun next() {
            finish()
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            canvas.drawColor(paper)
            y = TOP
        }

        fun finish() {
            val p = page ?: return
            // The cover carries no number; everything after it does, small and centred.
            if (number > 1) {
                val paint = text(sans, 7.5f, label)
                val s = "${number - 1}"
                p.canvas.drawText(s, (W - paint.measureText(s)) / 2, H - 22f, paint)
            }
            doc.finishPage(p)
            page = null
        }

        /** Start a new page unless [height] still fits on this one. */
        fun fit(height: Float): Boolean {
            if (page != null && y + height <= BOTTOM) return false
            next()
            return true
        }
    }

    // ---- Cover ------------------------------------------------------------------------

    /**
     * Every week's mark, 3 to 40, in a grid — the weeks you have lived through drawn in
     * full, the ones still to come held back. The one bold thing in the book.
     */
    private fun cover(pages: Pages, state: AppState, due: LocalDate) {
        pages.next()
        val c = pages.canvas
        val reached = Pregnancy.week(due, state.measuredOn(LocalDate.now()))

        val title = layout(state.displayName, text(serif, 40f, ink), CONTENT_W + (X0 - M))
        c.drawLayout(title, M, 64f)
        c.drawLayout(
            layout("Our pregnancy, week by week", text(sans, 11f, muted), CONTENT_W + (X0 - M)),
            M, 64f + title.height + 6f,
        )

        val cols = 6
        val cell = 50f
        val gridLeft = (W - cols * cell) / 2
        val gridTop = 150f
        val numberPaint = text(sansMedium, 6.5f, label)
        for (week in 3..40) {
            val i = week - 3
            val x = gridLeft + (i % cols) * cell
            val y = gridTop + (i / cols) * cell
            val lived = week <= reached
            drawMark(c, week, RectF(x + 10f, y + 4f, x + cell - 10f, y + cell - 14f), if (lived) 255 else 40)
            val n = "$week"
            numberPaint.alpha = if (lived) 255 else 110
            c.drawText(n, x + (cell - numberPaint.measureText(n)) / 2, y + cell - 4f, numberPaint)
        }

        val factPaint = text(sansMedium, 9f, muted)
        val left = "Due ${due.format(LONG)}"
        c.drawText(left, M, H - 40f, factPaint)
        state.bornOn?.let {
            val right = "Born ${it.format(LONG)}"
            c.drawText(right, W - M - factPaint.measureText(right), H - 40f, factPaint)
        }
    }

    // ---- Facts ------------------------------------------------------------------------

    private fun facts(pages: Pages, state: AppState, due: LocalDate) {
        pages.next()
        val live = state.withoutTombstones()

        heading(pages, "The record")

        val rows = buildList {
            if (state.babyName.isNotBlank()) add("Name" to state.babyName)
            if (state.babyNickname.isNotBlank()) add("Known as" to state.babyNickname)
            add("Due" to due.format(LONG))
            state.bornOn?.let {
                add("Born" to "${it.format(LONG)}, at ${Pregnancy.week(due, it)}+${Pregnancy.dayOfWeek(due, it)}")
            }
            add("Appointments" to count(live.appointments.size, "appointment"))
            add("Notes" to count(live.notes.size, "note"))
            add("Weights" to count(live.weights.size, "weight"))
            add("Kick counts" to count(live.kickSessions.size, "session"))
            add("Photos" to count(live.photos.size, "photo"))
            live.contacts.forEach { add(it.label to it.number) }
        }
        val keyPaint = text(sansMedium, 8.5f, label)
        val valuePaint = text(serif, 13f, ink)
        // The values line up after the longest label, which a contact's name can make long.
        val valueX = max(X0, M + rows.maxOf { keyPaint.measureText(it.first) } + 14f)
        for ((key, value) in rows) {
            val v = layout(value, valuePaint, W - M - valueX)
            pages.fit(max(v.height.toFloat(), 16f) + 10f)
            val c = pages.canvas
            c.drawText(key, M, pages.y + 13f, keyPaint)
            c.drawLayout(v, valueX, pages.y)
            pages.y += max(v.height.toFloat(), 16f) + 6f
            c.drawLine(M, pages.y - 3f, W - M, pages.y - 3f, hairline())
            pages.y += 4f
        }

        val weights = live.weights.sortedBy { it.date }
        if (weights.size >= 2) {
            pages.y += 22f
            pages.fit(170f)
            weightChart(pages.canvas, weights, RectF(M, pages.y + 26f, W - M, pages.y + 150f))
            pages.canvas.drawText("Weight", M, pages.y + 12f, text(serif, 13f, ink))
            pages.y += 170f
        }
    }

    /** A plain line through the weighings, first and last labelled. No judgement on it. */
    private fun weightChart(c: Canvas, weights: List<WeightEntry>, box: RectF) {
        val minKg = weights.minOf { it.kg }
        val maxKg = weights.maxOf { it.kg }
        val spanKg = max(maxKg - minKg, 1.0)
        val firstDay = weights.first().date.toEpochDay()
        val spanDays = max(weights.last().date.toEpochDay() - firstDay, 1L)
        val inner = RectF(box.left + 4f, box.top + 14f, box.right - 4f, box.bottom - 16f)

        fun at(w: WeightEntry) = Pair(
            inner.left + (w.date.toEpochDay() - firstDay).toFloat() / spanDays * inner.width(),
            inner.bottom - ((w.kg - minKg) / spanKg).toFloat() * inner.height(),
        )

        c.drawLine(box.left, box.bottom - 12f, box.right, box.bottom - 12f, hairline())
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Moss.toArgb(); style = Paint.Style.STROKE; strokeWidth = 1.3f
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        weights.forEachIndexed { i, w ->
            val (x, y) = at(w)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, line)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Moss.toArgb() }
        weights.forEach { val (x, y) = at(it); c.drawCircle(x, y, 1.8f, dot) }

        val kgPaint = text(sansMedium, 8f, ink)
        val datePaint = text(sans, 7.5f, label)
        val first = weights.first()
        val last = weights.last()
        val (fx, fy) = at(first)
        val (lx, ly) = at(last)
        c.drawText(kg(first.kg), fx, fy - 6f, kgPaint)
        val lastKg = kg(last.kg)
        c.drawText(lastKg, lx - kgPaint.measureText(lastKg), ly - 6f, kgPaint)
        c.drawText(first.date.format(SHORT), box.left, box.bottom, datePaint)
        val lastDate = last.date.format(SHORT)
        c.drawText(lastDate, box.right - datePaint.measureText(lastDate), box.bottom, datePaint)
    }

    // ---- Weeks ------------------------------------------------------------------------

    private suspend fun chapter(pages: Pages, chapter: Chapter, due: LocalDate) {
        val header = chapterHeader(chapter.week, due)
        // Built one moment at a time, so at most one week's next photo is in memory.
        var pending = blocks(chapter.moments.first())

        // A heading never sits alone at the foot of a page.
        pages.fit(HEADER_GAP + header.height + pending.first().height)
        pages.y += HEADER_GAP
        header.draw(pages.canvas, pages.y)
        pages.y += header.height

        val continued = text(sansMedium, 8f, label)
        var first = true
        chapter.moments.forEachIndexed { m, _ ->
            if (m > 0) pending = blocks(chapter.moments[m])
            pending.forEachIndexed { b, block ->
                val newPage = pages.fit(block.height + CONTINUED_H)
                // A week that runs over a page says which week it is at the top of the next.
                if (newPage) {
                    val s = chapter.week?.let { "Week $it, continued" } ?: "After the birth, continued"
                    pages.canvas.drawText(s, X0, pages.y + 8f, continued)
                    pages.y += CONTINUED_H
                }
                val top = pages.y
                val last = m == chapter.moments.lastIndex && b == pending.lastIndex
                // The thread down the left: through every moment of the week, ending on the last.
                pages.canvas.drawLine(
                    RAIL, if (newPage || first) top + 6f else top - 2f,
                    RAIL, if (last) top + 6f else top + block.height,
                    hairline(),
                )
                block.dot?.let {
                    pages.canvas.drawCircle(RAIL, top + 6f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it })
                }
                block.draw(pages.canvas, top)
                pages.y += block.height
                first = false
            }
        }
        pages.y += 18f
    }

    /** [dot] is null on the second and later pieces of a moment too long for one page. */
    private class Block(val height: Float, val dot: Int?, val draw: (Canvas, Float) -> Unit)

    private fun chapterHeader(week: Int?, due: LocalDate): Block {
        if (week == null) {
            val title = layout("After the birth", text(serif, 20f, clay), CONTENT_W + (X0 - M))
            return Block(title.height + 14f, clay) { c, y ->
                c.drawLayout(title, M, y)
                c.drawLine(M, y + title.height + 6f, W - M, y + title.height + 6f, hairline())
            }
        }
        val info = weeks[week]
        val start = Pregnancy.lmpFromDueDate(due).plusWeeks(week.toLong())
        val end = start.plusDays(6)
        val range = if (start.month == end.month) {
            "${start.dayOfMonth} to ${end.format(DAY_MONTH)}"
        } else {
            "${start.format(DAY_MONTH)} to ${end.format(DAY_MONTH)}"
        }
        val sizeLine = if (info.lengthCm != null && week >= 3) "About the size of ${lower(info.size)}" else null
        val detail = listOfNotNull(range, info.compactFigures.takeIf { it.isNotBlank() && week >= 3 })
            .joinToString(", ")

        val numberPaint = text(serif, 30f, clay)
        val sizePaint = text(serif, 14f, ink)
        val detailPaint = text(sans, 8.5f, muted)
        val markSize = 34f
        val textW = W - M - X0 - markSize - 8f
        val sizeLayout = layout(sizeLine ?: "Week $week", sizePaint, textW)
        val detailLayout = layout(detail, detailPaint, textW)
        val height = max(sizeLayout.height + 3f + detailLayout.height, markSize) + 14f

        return Block(height, clay) { c, y ->
            c.drawText("$week", M, y + 26f, numberPaint)
            c.drawLayout(sizeLayout, X0, y + 2f)
            c.drawLayout(detailLayout, X0, y + 5f + sizeLayout.height)
            if (week >= 3) drawMark(c, week, RectF(W - M - markSize, y, W - M, y + markSize), 255)
            c.drawLine(X0, y + height - 6f, W - M, y + height - 6f, hairline())
        }
    }

    /**
     * One moment, measured before it is drawn so pages break between moments. A moment
     * taller than a page — a long note — is cut between lines into several blocks, so
     * nothing ever runs off the bottom of a page.
     */
    private suspend fun blocks(m: Moment): List<Block> {
        val item = m.item
        val zone = ZoneId.systemDefault()
        val (kind, color, textColor) = when (item) {
            is Appointment -> Triple("Appointment", Palette.Slate.toArgb(), Palette.Slate.toArgb())
            is Note -> Triple("Note", clay, clay)
            is WeightEntry -> Triple("Weight", Palette.Moss.toArgb(), Palette.Moss.toArgb())
            is KickSession -> Triple("Kick count", Palette.Ochre.toArgb(), Palette.OchreText.toArgb())
            is Photo -> Triple("Photo", Palette.Rose.toArgb(), Palette.Rose.toArgb())
            is Contraction -> Triple("Contractions", Palette.Ochre.toArgb(), Palette.OchreText.toArgb())
            // Contacts are listed with the facts; the bag list stays in the data export.
            is Contact, is ChecklistItem -> error("not a timeline moment")
        }
        val dateText = when (item) {
            is Appointment -> Instant.ofEpochMilli(item.atMillis).atZone(zone).format(DATE_TIME)
            else -> m.date.format(DATE)
        }
        val datePaint = text(sansMedium, 8f, label)
        val kindPaint = text(sansBold, 7.5f, textColor)
        val dateLine = 13f

        val body: List<Part> = when (item) {
            is Appointment -> textParts(item.title, text(sansBold, 10.5f, ink)) +
                item.note.takeIf { it.isNotBlank() }?.let { textParts(it, text(sans, 9.5f, muted), gap = 2f) }.orEmpty()
            is Note -> textParts(item.text, text(serif, 11.5f, ink), spacing = 1.35f)
            is WeightEntry -> textParts(kg(item.kg), text(serif, 14f, ink))
            is KickSession ->
                textParts("${count(item.kicks, "kick")} in ${minutes(item.durationMillis)}", text(sansMedium, 10.5f, ink))
            is Photo -> photoParts(item)
            is Contraction -> contractionParts(m.contractions)
            is Contact, is ChecklistItem -> emptyList()
        }

        // Pack the parts into page-sized blocks; the first carries the date and the dot.
        val head = dateLine + 3f
        val groups = mutableListOf(mutableListOf<Part>())
        var used = head
        for (part in body) {
            if (used + part.height > MAX_BLOCK && groups.last().isNotEmpty()) {
                groups += mutableListOf<Part>()
                used = 0f
            }
            groups.last() += part
            used += part.height
        }
        return groups.mapIndexed { i, parts ->
            val top = if (i == 0) head else 0f
            val last = i == groups.lastIndex
            Block(top + parts.sumOf { it.height.toDouble() }.toFloat() + if (last) 16f else 0f, if (i == 0) color else null) { c, y0 ->
                if (i == 0) {
                    c.drawText(dateText, X0, y0 + 9f, datePaint)
                    c.drawText(kind, X0 + datePaint.measureText(dateText) + 8f, y0 + 9f, kindPaint)
                }
                var y = y0 + top
                for (part in parts) {
                    part.draw(c, y)
                    y += part.height
                }
            }
        }
    }

    /** A day of contractions as two plain lines: how many and when, then how long and how far apart. */
    private fun contractionParts(day: List<Contraction>): List<Part> {
        val zone = ZoneId.systemDefault()
        fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(CLOCK)
        val pairs = day.map { it.startedAtMillis to it.durationMillis }
        val headline = if (day.size == 1) {
            "1 contraction timed, at ${at(day.first().startedAtMillis)}"
        } else {
            "${day.size} contractions timed, ${at(day.first().startedAtMillis)} to ${at(day.last().startedAtMillis)}"
        }
        // The last hour of timing, measured back from the last one timed that day.
        val s = Contractions.summary(pairs, now = day.last().startedAtMillis)
        val detail = s?.let {
            listOfNotNull(
                "lasting about ${seconds(it.averageDurationMillis)}",
                it.averageGapMillis?.let { g -> "about ${seconds(g)} apart" },
            ).joinToString(", ").replaceFirstChar { c -> c.uppercase() } + " in the last hour timed."
        }
        return textParts(headline, text(sansMedium, 10.5f, ink)) +
            detail?.let { textParts(it, text(sans, 9.5f, muted), gap = 2f) }.orEmpty()
    }

    private class Part(val height: Float, val draw: (Canvas, Float) -> Unit)

    /** Text as parts of at most [MAX_PART] each, cut between lines, so a long note can span pages. */
    private fun textParts(
        s: String,
        paint: TextPaint,
        spacing: Float = 1.25f,
        gap: Float = 0f,
    ): List<Part> {
        val whole = layout(s, paint, CONTENT_W, spacing)
        if (whole.height <= MAX_PART) {
            return listOf(Part(whole.height + gap) { c, y -> c.drawLayout(whole, X0, y + gap) })
        }
        val parts = mutableListOf<Part>()
        var from = 0
        while (from < whole.lineCount) {
            var to = from
            while (to + 1 < whole.lineCount && whole.getLineBottom(to + 1) - whole.getLineTop(from) <= MAX_PART) to++
            val piece = layout(
                s.substring(whole.getLineStart(from), whole.getLineEnd(to)).trimEnd(),
                paint, CONTENT_W, spacing,
            )
            val g = if (from == 0) gap else 0f
            // A layout drops the extra leading after its last line; put it back at each
            // cut so the join is spaced like every other line of the note.
            val lead = if (to + 1 < whole.lineCount) paint.fontSpacing * (spacing - 1f) else 0f
            parts += Part(piece.height + g + lead) { c, y -> c.drawLayout(piece, X0, y + g) }
            from = to + 1
        }
        return parts
    }

    private suspend fun photoParts(photo: Photo): List<Part> {
        // About 300pt wide in print; 1000px is past 200dpi there, and a third of the memory.
        val bitmap: Bitmap? = photos.decode(photo.blob, maxPx = 1000)
        val parts = mutableListOf<Part>()
        if (bitmap == null) {
            unplaced++
            parts += textParts("This photo couldn't be added — it may not have reached this phone yet.", text(sans, 9f, muted))
        } else {
            val scale = min(CONTENT_W / bitmap.width, PHOTO_MAX_H / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            parts += Part(h + 4f) { c, y ->
                val dst = RectF(X0, y + 2f, X0 + w, y + 2f + h)
                c.save()
                c.clipPath(Path().apply { addRoundRect(dst, 3f, 3f, Path.Direction.CW) })
                c.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                c.restore()
                bitmap.recycle()
            }
        }
        if (photo.caption.isNotBlank()) parts += textParts(photo.caption, text(serif, 10f, muted), gap = 4f)
        return parts
    }

    // ---- Birth and colophon -----------------------------------------------------------

    private fun birth(pages: Pages, due: LocalDate, born: LocalDate) {
        // A rule sets the birth apart from the weeks above it; at the top of a page there
        // is nothing to set it apart from.
        if (!pages.fit(110f)) {
            pages.y += 18f
            pages.canvas.drawLine(M, pages.y, W - M, pages.y, hairline())
            pages.y += 20f
        }
        val c = pages.canvas
        val title = layout("Born ${born.format(LONG)}", text(serif, 24f, clay), W - 2 * M)
        c.drawLayout(title, M, pages.y)
        pages.y += title.height + 6f
        val at = layout(
            "At ${Pregnancy.week(due, born)} weeks and ${days(Pregnancy.dayOfWeek(due, born))}",
            text(sans, 10f, muted), W - 2 * M,
        )
        c.drawLayout(at, M, pages.y)
        pages.y += at.height + 30f
    }

    private fun colophon(pages: Pages) {
        val note = layout(
            "The sizes and figures for each week are population averages, not measurements " +
                "of your baby. Made with Bump on ${LocalDate.now().format(LONG)}.",
            text(sans, 8f, label), W - 2 * M, 1.35f,
        )
        pages.fit(note.height + 30f)
        pages.y = max(pages.y + 10f, BOTTOM - note.height - 10f)
        pages.canvas.drawLine(M, pages.y, M + 40f, pages.y, hairline())
        pages.canvas.drawLayout(note, M, pages.y + 10f)
    }

    // ---- Drawing helpers --------------------------------------------------------------

    private fun heading(pages: Pages, s: String) {
        val l = layout(s, text(serif, 24f, ink), W - 2 * M)
        pages.canvas.drawLayout(l, M, pages.y)
        pages.y += l.height + 18f
    }

    private val markCache = HashMap<Int, Bitmap>()

    /**
     * The week's vector mark, rasterised at four times its printed size first. Drawn
     * straight onto a PDF canvas a vector drawable caches itself at 1px per point, which
     * prints soft.
     */
    private fun drawMark(c: Canvas, week: Int, dst: RectF, alpha: Int) {
        val bitmap = markCache.getOrPut(week) {
            val d = ContextCompat.getDrawable(ctx, weekMark(week))!!
            val px = 160
            createBitmap(px, px).also { b ->
                d.setBounds(0, 0, px, px)
                d.draw(Canvas(b))
            }
        }
        c.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
    }

    private fun hairline() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule; strokeWidth = 0.6f }

    private fun text(face: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
    }

    private fun layout(s: CharSequence, paint: TextPaint, width: Float, spacing: Float = 1.2f): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .build()

    private fun Canvas.drawLayout(l: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        l.draw(this)
        restore()
    }

    private companion object {
        // A5 in points.
        const val W = 420
        const val H = 595
        const val M = 36f
        const val TOP = 44f
        const val BOTTOM = H - 48f

        /** The timeline thread, between the week numbers and the text. */
        const val RAIL = 76f
        const val X0 = 90f
        const val CONTENT_W = W - M - X0
        const val PHOTO_MAX_H = 230f
        const val CONTINUED_H = 20f
        const val HEADER_GAP = 6f
        /**
         * Long text is cut into pieces of about ten lines, one per block, so a long note
         * starts under whatever came before it and fills the page rather than jumping to
         * the next one whole. A block holding one tall part (a photo) is still allowed.
         */
        const val MAX_PART = 150f
        const val MAX_BLOCK = 170f

        val LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)
        val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.UK)

        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

        /** "45 seconds", "4 min 10 s" — a length a person says out loud. */
        fun seconds(ms: Long): String {
            val s = (ms / 1000).toInt()
            return if (s < 60) "$s seconds" else if (s % 60 == 0) "${s / 60} min" else "${s / 60} min ${s % 60} s"
        }

        fun kg(v: Double) = "%.1f kg".format(Locale.UK, v)
        fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
        fun days(n: Int) = if (n == 1) "1 day" else "$n days"
        fun minutes(ms: Long): String {
            val m = (ms / 60_000).toInt()
            return if (m < 1) "under a minute" else if (m == 1) "1 minute" else "$m minutes"
        }

        /** "Ear of corn" reads "about the size of an ear of corn"; proper nouns don't occur. */
        fun lower(size: String): String {
            val s = size.replaceFirstChar { it.lowercase() }
            val article = if (s.first() in "aeiou") "an" else "a"
            return if (s.endsWith("s") && !s.endsWith("ss")) s else "$article $s"
        }
    }
}
