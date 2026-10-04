import {defineConfig} from 'vitest/config';
// Each suite owns subprocess/FIFO fixtures. Keep memory bounded alongside Android tooling.
export default defineConfig({test:{setupFiles:['tests/setup.ts'],include:['tests/*.test.ts'],fileParallelism:false,maxWorkers:1}});
