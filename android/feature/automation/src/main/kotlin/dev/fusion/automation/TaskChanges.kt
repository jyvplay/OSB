package dev.fusion.automation

import java.util.Collections

/** Immutable local undo capability. Exact data AND revision binding; not authentication/rollback protection. */
class TaskChange private constructor(val before:TaskBook,val after:TaskBook) {
    fun matchesBefore(book:TaskBook)=same(before,book)
    fun matchesAfter(book:TaskBook)=same(after,book)
    fun undo(current:TaskBook):TaskBook {
        require(matchesAfter(current)) { "Undo is stale or belongs to different task data" }
        return TaskBook(Math.incrementExact(current.revision),before.tasks)
    }
    companion object {
        fun same(a:TaskBook,b:TaskBook)=a.revision==b.revision && a.tasks==b.tasks
        fun prepare(before:TaskBook,after:TaskBook):TaskChange? {
            if(same(before,after)) return null
            require(after.revision==Math.incrementExact(before.revision) && after.tasks!=before.tasks)
            // Refuse BEFORE writing if immediate undo cannot preserve the archived-item reserve.
            TaskBook(Math.incrementExact(after.revision),before.tasks)
            return TaskChange(before,after)
        }
    }
}
data class TaskCommit(val book:TaskBook,val undo:TaskChange?)

/** Append-only merge. Never replaces/deletes existing data or adopts an imported revision. */
class TaskMergePreview private constructor(val before:TaskBook,val sourceRevision:Long,
    added:List<LocalTask>,val identical:Int,val change:TaskChange?) {
    val added:List<LocalTask> = Collections.unmodifiableList(ArrayList(added))
    fun apply(current:TaskBook):TaskBook {
        require(TaskChange.same(before,current)) { "Merge preview is stale or belongs to different data" }
        return change?.after ?: current
    }
    companion object {
        fun inspect(current:TaskBook,incoming:TaskBook):TaskMergePreview {
            val existing=current.tasks.associateBy {it.id};val added=ArrayList<LocalTask>();var identical=0
            incoming.tasks.forEach {task ->
                val old=existing[task.id]
                if(old==null) added+=task else {
                    require(old==task) { "Conflicting task ID ${task.id}; no tasks merged" };identical++
                }
            }
            require(current.tasks.size+added.size<=128) { "Merge exceeds active+archived capacity" }
            val after=if(added.isEmpty()) current else TaskBook(Math.incrementExact(current.revision),current.tasks+added)
            return TaskMergePreview(current,incoming.revision,added,identical,TaskChange.prepare(current,after))
        }
    }
}
