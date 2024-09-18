package com.github.jozott00.wokwiintellij

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.WokwiBundle"

internal object WokwiBundle {
  private val INSTANCE = DynamicBundle(WokwiBundle::class.java, BUNDLE)
//  private val INSTANCE = Dynamic

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    INSTANCE.getMessage(key, *params)

  @Suppress("unused")
  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    INSTANCE.getLazyMessage(key, *params)
}
