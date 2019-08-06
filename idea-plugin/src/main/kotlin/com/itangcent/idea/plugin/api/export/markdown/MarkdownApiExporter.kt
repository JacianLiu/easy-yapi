package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.RequestHelper
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.traceError
import java.util.*

@Singleton
class MarkdownApiExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val requestHelper: RequestHelper? = null

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private val markdownFormatter: MarkdownFormatter? = null

    fun export() {

        logger!!.info("Start find apis...")
        val requests: MutableList<Request> = Collections.synchronizedList(ArrayList<Request>())

        SelectedHelper.Builder()
                .dirFilter { dir, callBack ->
                    actionContext!!.runInSwingUI {
                        try {
                            val project = actionContext.instance(Project::class)
                            val yes = Messages.showYesNoDialog(project,
                                    "Export the api in directory [${ActionUtils.findCurrentPath(dir)}]?",
                                    "Are you sure",
                                    Messages.getQuestionIcon())
                            if (yes == Messages.YES) {
                                callBack(true)
                            } else {
                                logger.debug("Cancel the operation export api from [${ActionUtils.findCurrentPath(dir)}]!")
                                callBack(false)
                            }
                        } catch (e: Exception) {
                            callBack(false)
                        }
                    }
                }
                .fileFilter { file -> file.name.endsWith(".java") }
                .classHandle {
                    actionContext!!.checkStatus()
                    classExporter!!.export(it, requestHelper!!) { request -> requests.add(request) }
                }
                .onCompleted {
                    try {
                        if (classExporter is Worker) {
                            classExporter.waitCompleted()
                        }
                        if (requests.isEmpty()) {
                            logger.info("No api be found to export!")
                            return@onCompleted
                        }
                        logger.info("Start parse apis")
                        val apiInfo = markdownFormatter!!.parseRequests(requests)
                        requests.clear()
                        actionContext!!.runAsync {
                            try {
                                fileSaveHelper!!.saveOrCopy(apiInfo, {
                                    logger.info("Exported data are copied to clipboard,you can paste to a md file now")
                                }, {
                                    logger.info("Apis save success")
                                }) {
                                    logger.info("Apis save failed")
                                }
                            } catch (e: Exception) {
                                logger.error("Apis save failed")
                                logger.traceError(e)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Apis save failed")
                        logger.traceError(e)
                    }
                }
                .traversal()
    }
}