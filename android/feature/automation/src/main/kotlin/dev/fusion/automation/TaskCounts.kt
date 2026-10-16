package dev.fusion.automation

import dev.fusion.ooxml.LiteralCell
import dev.fusion.ooxml.LiteralWorkbook
import dev.fusion.ooxml.WorkbookExport

/** Deliberate aggregate projection. Not anonymity, differential privacy, a backup, or arbitrary-file metadata removal. */
data class TaskCounts(val activeOpen:Int,val activeDone:Int,val archivedOpen:Int,val archivedDone:Int) {
    init {require(listOf(activeOpen,activeDone,archivedOpen,archivedDone).all {it in 0..128});require(total<=128)}
    val total:Int get()=activeOpen+activeDone+archivedOpen+archivedDone
    companion object {
        fun from(book:TaskBook)=TaskCounts(book.tasks.count {!it.archived && !it.done},book.tasks.count {!it.archived && it.done},
            book.tasks.count {it.archived && !it.done},book.tasks.count {it.archived && it.done})
    }
    fun export():WorkbookExport {
        val rows=listOf(listOf(LiteralCell.Text("State"),LiteralCell.Text("Open"),LiteralCell.Text("Completed")),
            listOf(LiteralCell.Text("Active"),LiteralCell.Number(activeOpen.toBigDecimal()),LiteralCell.Number(activeDone.toBigDecimal())),
            listOf(LiteralCell.Text("Archived"),LiteralCell.Number(archivedOpen.toBigDecimal()),LiteralCell.Number(archivedDone.toBigDecimal())))
        return LiteralWorkbook.export("Counts",rows).let {WorkbookExport(it.bytes,it.limitations+
            "Counts only: task titles, IDs, dates, order and revision omitted. Counts, filename and sharing context can still identify people. Not a task backup.")}
    }
}
