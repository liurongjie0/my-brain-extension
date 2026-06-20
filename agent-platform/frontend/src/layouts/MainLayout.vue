<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" style="background:#304156">
      <div style="color:#fff;padding:16px;font-weight:bold">Agent 平台</div>
      <el-menu :default-active="route.path" router background-color="#304156"
               text-color="#fff" active-text-color="#ffd04b">
        <el-menu-item index="/chat">对话</el-menu-item>
        <el-menu-item index="/admin/agents">Agent 管理</el-menu-item>
        <el-menu-item index="/admin/knowledge">知识库</el-menu-item>
        <el-menu-item index="/admin/tools">工具</el-menu-item>
        <el-menu-item index="/admin/conversations">会话审计</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main style="padding:0"><router-view /></el-main>
  </el-container>
</template>
