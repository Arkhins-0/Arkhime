package ani.arkhime.com.forum

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.content.ContextCompat
import ani.arkhime.com.R
import ani.arkhime.com.buildMarkwon
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.anilist.api.ThreadComment
import ani.arkhime.com.databinding.ItemThreadCommentBinding
import ani.arkhime.com.loadImage
import ani.arkhime.com.profile.activity.ActivityItemBuilder
import ani.arkhime.com.snackString
import ani.arkhime.com.util.ActivityMarkdownCreator
import ani.arkhime.com.util.AniMarkdown
import ani.arkhime.com.util.customAlertDialog
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.viewbinding.BindableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreadCommentItem(
    val comment: ThreadComment,
    val threadId: Int,
    val context: Context,
    val parentAdapter: GroupieAdapter,
    val scope: CoroutineScope
) : BindableItem<ItemThreadCommentBinding>() {

    override fun bind(viewBinding: ItemThreadCommentBinding, position: Int) {
        viewBinding.commentAvatar.loadImage(comment.user?.avatar?.medium)
        viewBinding.commentAuthor.text = comment.user?.name ?: "Unknown"
        viewBinding.commentTime.text = comment.createdAt?.let { ActivityItemBuilder.getDateTime(it) } ?: ""
        val markwon = buildMarkwon(context, false, anilist = true)
        val html = AniMarkdown.getBasicAniHTML(comment.comment ?: "")
        markwon.setMarkdown(viewBinding.commentBody, html)

        val likeColor = ContextCompat.getColor(context, R.color.yt_red)
        val notLikeColor = ContextCompat.getColor(context, R.color.bg_opp)
        viewBinding.commentLikeIcon.setColorFilter(if (comment.isLiked == true) likeColor else notLikeColor)
        viewBinding.commentLikeCount.text = (comment.likeCount ?: 0).toString()

        viewBinding.commentLikeButton.setOnClickListener {
            scope.launch(Dispatchers.IO) {
                val success = Anilist.mutation.toggleLike(comment.id, "THREAD_COMMENT") != null
                withContext(Dispatchers.Main) {
                    if (success) {
                        comment.isLiked = !(comment.isLiked ?: false)
                        val count = comment.likeCount ?: 0
                        comment.likeCount = if (comment.isLiked == true) count + 1 else (count - 1).coerceAtLeast(0)
                        viewBinding.commentLikeCount.text = (comment.likeCount ?: 0).toString()
                        viewBinding.commentLikeIcon.setColorFilter(if (comment.isLiked == true) likeColor else notLikeColor)
                    }
                }
            }
        }

        viewBinding.commentDelete.visibility = if (comment.userId == Anilist.userid) View.VISIBLE else View.GONE
        viewBinding.commentDelete.setOnClickListener {
            context.customAlertDialog().apply {
                setTitle(R.string.delete)
                setMessage(R.string.delete_comment_confirm)
                setPosButton(R.string.delete) {
                    scope.launch(Dispatchers.IO) {
                        val success = Anilist.mutation.deleteThreadComment(comment.id)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                snackString("Comment deleted")
                                parentAdapter.remove(this@ThreadCommentItem)
                            } else {
                                snackString("Failed to delete comment")
                            }
                        }
                    }
                }
                setNegButton(R.string.cancel)
                show()
            }
        }

        viewBinding.commentReplyButton.setOnClickListener {
            context.startActivity(
                Intent(context, ActivityMarkdownCreator::class.java).apply {
                    putExtra("type", "threadComment")
                    putExtra("threadId", threadId)
                    putExtra("parentId", comment.id)
                    putExtra("other", "@${comment.user?.name} ")
                }
            )
        }
    }

    override fun getLayout(): Int = R.layout.item_thread_comment

    override fun initializeViewBinding(view: View): ItemThreadCommentBinding =
        ItemThreadCommentBinding.bind(view)
}
