let scriptPath = files.join(files.cwd(), 'timed-task-demo.js');

let task = tasks.addDisposableTask({
    path: scriptPath,
    date: new Date(Date.now() + 60e3),
    maxRetries: 2,
    retryBackoffMillis: 30e3,
    mutex: true,
    timeoutMillis: 5 * 60e3,
});

console.log('任务 ID: ' + task.id);

let queue = tasks.queryTimedTaskQueue();
console.log('当前队列:');
queue.forEach(function (item) {
    console.log(JSON.stringify({
        taskId: item.taskId,
        backend: item.backend,
        scheduled: item.scheduled,
        nextScheduledAt: new Date(item.nextScheduledAt).toISOString(),
        maxRetries: item.maxRetries,
        timeoutMillis: item.timeoutMillis,
    }));
});

let runs = tasks.queryTimedTaskRuns({ taskId: task.id, limit: 10 });
console.log('最近运行记录数量: ' + runs.length);
runs.forEach(function (run) {
    console.log(JSON.stringify({
        eventType: run.eventType,
        launchStatus: run.launchStatus,
        finishStatus: run.finishStatus,
        backend: run.backend,
        duration: run.duration,
        exception: run.exception,
        retryAttempt: run.retryAttempt,
    }));
});
