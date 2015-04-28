using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace HPLab.Enities
{
    public enum FutureState
    {
        None,
        Created,
        InProgress,
        Success,
        Faulted,
        Canceled
    }

    public class Future<T>
    {
        private readonly int _id;
        private readonly SimpleThreadScheduler _scheduler;

        public Future(int id, SimpleThreadScheduler scheduler)
        {
            _id = id;
            _scheduler = scheduler;
        }

        public Future(int id)
            : this(id, SimpleThreadScheduler.Default)
        {

        }

        internal List<Future<T>> ChildTasks { get; set; }

        public FutureState State { get; internal set; }

        public ApplicationException ExecutionException { get; internal set; }

        public T Result
        {
            get
            {
                return (T)_scheduler.GetResult(_id);
            }
        }

        public bool Cancel()
        {
            return _scheduler.Cancel(_id);
        }

        public bool Fail(ApplicationException ex)
        {
            return _scheduler.Fail(_id, ex);
        }
    }
}