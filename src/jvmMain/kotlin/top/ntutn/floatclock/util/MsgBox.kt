package top.ntutn.floatclock.util

import java.awt.Component
import java.awt.HeadlessException
import javax.swing.Icon
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.WindowConstants

class MsgBox private constructor(private val parentComponent: Component?) {
    companion object {
        /**
         * copied from javax.swing.JOptionPane.createDialog(java.awt.Component, java.lang.String, int)
         */
        @Throws(HeadlessException::class)
        private fun showOptionDialog(
            parentComponent: Component?,
            message: Any?, title: String?, optionType: Int, messageType: Int,
            icon: Icon?, options: Array<Any>?, initialValue: Any?, model: Boolean = true
        ): Int {
            val pane = JOptionPane(
                message, messageType,
                optionType, icon,
                options, initialValue
            )
            pane.initialValue = initialValue
            pane.componentOrientation = (parentComponent ?: JOptionPane.getRootFrame()).componentOrientation
            val getStyleMethod = JOptionPane::class.java.getDeclaredMethod("styleFromMessageType", Int::class.java)
            getStyleMethod.isAccessible = true
            val style = getStyleMethod.invoke(null, messageType) as Int
            // val style = JOptionPane.styleFromMessageType(messageType)
            val createDialogMethod = pane.javaClass.getDeclaredMethod("createDialog", Component::class.java, String::class.java, Int::class.java)
            createDialogMethod.isAccessible = true
            val dialog = createDialogMethod.invoke(pane, parentComponent, title, style) as JDialog
//            val dialog = pane.createDialog(parentComponent, title, style)
            pane.selectInitialValue()
            dialog.isModal = model
            dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            dialog.isAlwaysOnTop = !model
            dialog.show()
//            dialog.dispose()
            val selectedValue = pane.value ?: return JOptionPane.CLOSED_OPTION
            if (options == null) {
                return if (selectedValue is Int) selectedValue.toInt() else JOptionPane.CLOSED_OPTION
            }
            var counter = 0
            val maxCounter = options.size
            while (counter < maxCounter) {
                if (options[counter] == selectedValue) return counter
                counter++
            }
            return JOptionPane.CLOSED_OPTION
        }
    }

    enum class MessageType(val value: Int) {
        ERROR(0),
        INFORMATION(1),
        WARNING(2),
        QUESTION(3),
        PLAIN(-1)
    }

    var title: String? = null
    var message: String? = null
    var messageType = MessageType.PLAIN
    var model: Boolean = true

    class Builder(parentComponent: Component? = null) {
        val msgbox = MsgBox(parentComponent)

        private inline fun builderImpl(crossinline block: MsgBox.() -> Unit): Builder {
            block(msgbox)
            return this
        }

        fun title(string: String) = builderImpl {
            title = string
        }

        fun message(string: String) = builderImpl {
            message = string
        }

        fun type(type: MessageType) = builderImpl {
            messageType = type
        }

        fun model() = builderImpl {
            model = true
        }

        fun noneModel() = builderImpl {
            model = false
        }

        fun build() = msgbox
    }

    fun show() {
        showOptionDialog(parentComponent, message, title, JOptionPane.DEFAULT_OPTION, messageType.value, null, null, null, model = model)
    }
}