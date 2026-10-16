package dev.fusion.automation

import dev.fusion.ooxml.LiteralCell
import dev.fusion.ooxml.LiteralWorkbook
import dev.fusion.ooxml.LiteralWorksheet
import dev.fusion.ooxml.WorkbookExport

/** Explicit report, NOT a task backup/import format. No formulas, date serials, macros or existing-file mutation. */
object TaskWorkbook {
    fun export(book:TaskBook):WorkbookExport {
        val header=listOf("ID","Title","Date (text)","Completed","Archived").map {LiteralCell.Text(it)}
        val sheets=listOf(false,true).map {archived ->
            val rows=book.tasks.filter {it.archived==archived}.map {task ->
                listOf(LiteralCell.Text(task.id),LiteralCell.Text(task.title),LiteralCell.Text(task.due?.toString() ?: ""),
                    LiteralCell.Bool(task.done),LiteralCell.Bool(task.archived))
            }
            LiteralWorksheet(if(archived) "Archived" else "Active",listOf(header)+rows)
        }
        val result=LiteralWorkbook.exportSheets(sheets)
        require(result.bytes.size<=512*1024)
        return WorkbookExport(result.bytes,result.limitations+"Task report only: date cells are text; use FTS export for merge-compatible backup. No formulas are executed.")
    }
}
