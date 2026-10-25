<script setup lang="ts">
import IndexCards from '@/components/IndexCards.vue'
import RankTable from '@/components/RankTable.vue'
import StockSearch from '@/components/StockSearch.vue'
import WatchlistPanel from '@/components/WatchlistPanel.vue'
</script>

<template>
    <div class="home">
        <div class="home-bar">
            <StockSearch width="260px" placeholder="搜索股票代码 / 名称" />
        </div>

        <IndexCards />

        <div class="grid">
            <WatchlistPanel class="left" />
            <RankTable class="right" />
        </div>
    </div>
</template>

<style scoped>
.home {
    display: flex;
    flex-direction: column;
    gap: 10px;
    height: 100%;
    min-height: 0;
}

.home-bar {
    display: flex;
    justify-content: flex-end;
    flex: 0 0 auto;
}

.grid {
    display: grid;
    grid-template-columns: 368px minmax(0, 1fr);
    gap: 10px;
    flex: 1 1 auto;
    min-height: 0;
}
</style>
