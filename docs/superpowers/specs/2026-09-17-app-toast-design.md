# AppToast 统一提示 设计说明

**日期：** 2026-09-17  
**状态：** 已确认并实现中  

## 目标

将工程内 `Toast.makeText` 收敛到 `AppToast` 工具类，并采用深色圆角自定义样式。

## 约定

- API：`show(Context, CharSequence|resId)`、`showLong(...)`
- UI：半透明深灰圆角条、白字 14sp、底部居中（避开底栏约 80dp）
- 主线程安全；展示前取消上一条 AppToast，减轻叠层
- 不引入 Snackbar；不改业务文案

## 非目标

- 成功/失败彩色类型系统
- 队列化多条排队动画
