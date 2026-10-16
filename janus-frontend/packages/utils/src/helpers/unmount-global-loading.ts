/**
 * 移除并销毁loading
 * 放在这里是而不是放在 index.html 的app标签内，是因为这样比较不会生硬，渲染过快可能会有闪烁
 * 通过先添加css动画隐藏，在动画结束后在移除loading节点来改善体验
 * 不好的地方是会增加一些代码量
 * 自定义loading可在应用入口的 appLoading 配置中调整。
 */
export function unmountGlobalLoading() {
  // 查找全局 loading 元素并立即移除，杜绝等待动画卡顿感
  const loadingElement = document.querySelector('#__app-loading__');
  if (loadingElement) {
    loadingElement.remove();
  }

  const injectLoadingElements = document.querySelectorAll(
    '[data-app-loading^="inject"]',
  );
  injectLoadingElements.forEach((el) => el.remove());
}
