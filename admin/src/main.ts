import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { createPinia } from 'pinia';
import 'element-plus/dist/index.css';

import App from './App.vue';
import { router } from './router';
import './styles/tokens.css';

const app = createApp(App);

// pinia 必须先于 router 安装：路由守卫内会读取会话 store。
app.use(createPinia());
app.use(router);
app.use(ElementPlus, { locale: zhCn });
app.mount('#app');
