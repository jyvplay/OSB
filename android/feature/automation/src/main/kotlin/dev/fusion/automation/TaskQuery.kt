package dev.fusion.automation

import java.util.Collections
import java.util.Locale

enum class TaskScope { ACTIVE, OPEN, COMPLETED, ARCHIVED }
enum class TaskOrder { INSERTION, DUE }

/** Literal Locale.ROOT lowercase matching, not regex, normalization or linguistic collation.
 * Query projection never changes the snapshot/revision. Date order is ascending/null-last,
 * then original insertion index. Archived records still consume the shared128-record cap.
 */
object TaskQuery {
    fun select(book: TaskBook, text: String = "", scope: TaskScope = TaskScope.ACTIVE,
               order: TaskOrder = TaskOrder.INSERTION): List<LocalTask> {
        require(text.length <= 256 && text.none { it.isISOControl() })
        LocalTask.strictBytes(text)
        val query=text.lowercase(Locale.ROOT)
        val matches=book.tasks.withIndex().filter { (_,t) ->
            val inScope=when(scope) {
                TaskScope.ACTIVE -> !t.archived
                TaskScope.OPEN -> !t.archived && !t.done
                TaskScope.COMPLETED -> !t.archived && t.done
                TaskScope.ARCHIVED -> t.archived
            }
            inScope && t.title.lowercase(Locale.ROOT).contains(query)
        }
        val sorted=if(order==TaskOrder.INSERTION) matches else matches.sortedWith(
            compareBy<IndexedValue<LocalTask>> { it.value.due == null }.thenBy { it.value.due }.thenBy { it.index })
        return Collections.unmodifiableList(sorted.map { it.value })
    }
}
