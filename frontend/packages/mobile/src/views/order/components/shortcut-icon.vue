<template>
  <span class="order-shortcut-icon" aria-hidden="true">
    <img :src="orderIcon" alt="" width="42" height="42" />
    <span v-if="create" class="order-shortcut-icon__badge">+</span>
  </span>
</template>

<script setup lang="ts">
  import orderIcon from '@/assets/images/order-shortcut.png';

  defineProps<{ create?: boolean }>();
</script>

<style lang="less" scoped>
  .order-shortcut-icon {
    position: relative;
    display: inline-block;
    width: 30px;
    height: 30px;
    img {
      position: absolute;
      top: -6px;
      left: -6px;
      max-width: none;
    }
    &__badge {
      position: absolute;
      right: -1px;
      bottom: -1px;
      display: flex;
      justify-content: center;
      align-items: center;
      width: 15px;
      height: 15px;
      font-size: 16px;
      font-weight: 700;
      border: 0.2px solid white;
      border-radius: 50%;
      color: white;
      background: linear-gradient(135deg, #e9e1ffb3, #f7f4ff);
      box-shadow: inset 0 0 3px #a78bfa66;
      text-shadow: 0 1px 2px #9472d5;
      line-height: 15px;
    }
  }
</style>
