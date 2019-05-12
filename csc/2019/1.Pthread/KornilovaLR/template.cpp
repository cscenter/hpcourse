#include <pthread.h>
#include <iostream>
#include <vector>
#include <sstream>
#include <unistd.h>
#include <assert.h>
#include <atomic>


#define NOERROR 0
#define OVERFLOW 1

unsigned         sleep_time_ms;
thread_local int tls_error_code = NOERROR;
unsigned         n;
int              consumer_thread_started = 0; // accessed inside synchronized blocks
std::atomic<bool> stop(false); // accessed by interruptor thread without using mutex
pthread_cond_t   consumer_started_cond  = PTHREAD_COND_INITIALIZER;
pthread_cond_t   update_ready_cond      = PTHREAD_COND_INITIALIZER;
pthread_cond_t   update_processed_cond  = PTHREAD_COND_INITIALIZER;
pthread_mutex_t  consumer_started_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t  update_mutex           = PTHREAD_MUTEX_INITIALIZER;

struct update {
  int value;
  bool processed;
};

struct result {
  const int sum;
  const int error_code;
};

int get_last_error() {
  return tls_error_code;
}


void set_last_error(int code) {
  tls_error_code = code;
}

void do_update(update &current_update, int number, bool finish) {
  pthread_mutex_lock(&update_mutex);
  while (!current_update.processed) {
    pthread_cond_wait(&update_processed_cond, &update_mutex);
  }
  if (finish) {
    stop = true;
    pthread_cond_broadcast(&update_ready_cond);
  } else {
    current_update.value = number;
    current_update.processed = false;
    pthread_cond_signal(&update_ready_cond);
  }

  pthread_mutex_unlock(&update_mutex);
}

/**
 * wait for consumer to start
 * read data, loop through each value and update the value, notify consumer, wait for consumer to process
 */
void *producer_routine(void *upd) {
  // wait for consumers
  pthread_mutex_lock(&consumer_started_mutex);
  while (consumer_thread_started != n) {
    pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
  }
  pthread_mutex_unlock(&consumer_started_mutex);

  update &current_update = *((update *) upd);
  int number;
  while (std::cin >> number) {
    do_update(current_update, number, false);
  }
  do_update(current_update, 0, true);
  pthread_exit(nullptr);
}


struct timespec get_timespec(unsigned time_ms) {
  if (time_ms == 0) return timespec{0, 0};
  return timespec{time_ms / 1000, (time_ms % 1000) * 1000 * 1000};
}


void maybe_sleep() {
  if (sleep_time_ms == 0) return;
  struct timespec ts = get_timespec(rand() % ((int) sleep_time_ms));
  if (ts.tv_sec != 0 || ts.tv_nsec != 0) {
    nanosleep(&ts, nullptr);
  }
}

int update_sum(int a, int b) {
  int sum = a + b;
  if (b >= 0 && sum >= a) return sum;
  if (b < 0 && sum < a) return sum;
  set_last_error(OVERFLOW);
  return sum;
}

/**
 * notify about start
 * for every update issued by producer, read the value and add to sum
 * return pointer to result (for particular consumer)
 */
void *consumer_routine(void *upd) {
  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

  // notify
  pthread_mutex_lock(&consumer_started_mutex);
  consumer_thread_started++;
  pthread_cond_broadcast(&consumer_started_cond); // broadcast to producer and interruptor
  pthread_mutex_unlock(&consumer_started_mutex);

  update &current_update = *((update *) upd);
  int local_sum = 0;
  while (true) {
    pthread_mutex_lock(&update_mutex);
    while (!stop && current_update.processed) {
      pthread_cond_wait(&update_ready_cond, &update_mutex);
    }
    if (stop) {
      pthread_mutex_unlock(&update_mutex);
      pthread_exit(new result{local_sum, get_last_error()});
    }
    current_update.processed = true;
    int delta = current_update.value;
    pthread_cond_signal(&update_processed_cond);
    pthread_mutex_unlock(&update_mutex);

    local_sum = update_sum(local_sum, delta);
    if (get_last_error() != NOERROR) pthread_exit(new result{local_sum, get_last_error()});
    maybe_sleep();
  }
}

/**
 * wait for consumers to start
 * interrupt random consumer while producer is running
 */
void *consumer_interruptor_routine(void *arg) {
  pthread_mutex_lock(&consumer_started_mutex);
  while(consumer_thread_started != n) {
    pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
  }
  pthread_mutex_unlock(&consumer_started_mutex);

  auto &consumers = *((std::vector<pthread_t> *) arg);
  while (!stop) {
    pthread_cancel(consumers[rand() % consumers.size()]);
  }
  pthread_exit(nullptr);
}

int start_consumer_threads(std::vector<pthread_t> &consumer_threads, update *update_ptr) {
  for (auto & consumer_thread : consumer_threads) {
    int code = pthread_create(&consumer_thread, nullptr, consumer_routine, update_ptr);
    if (code != 0) {
      std::cout << "error code: " << code << std::endl;
      return code;
    }
  }
  return 0;
}

int start_interruptor_thread(pthread_t &interruptor_thread, std::vector<pthread_t> *consumer_threads) {
  int code = pthread_create(&interruptor_thread, nullptr, consumer_interruptor_routine, consumer_threads);
  if (code != 0) std::cout << "error code: " << code << std::endl;
  return code;
}

int start_producer_thread(pthread_t &producer_thread, update *current_update) {
  int res = pthread_create(&producer_thread, nullptr, producer_routine, current_update);
  if (res != 0) {
    std::cout << "error code: " << res << std::endl;
    return res;
  }
  return 0;
}

result get_result(const std::vector<pthread_t> &consumer_threads) {
  int sum = 0;
  for (auto & consumer_thread : consumer_threads) {
    void *res_ptr = nullptr;
    pthread_join(consumer_thread, &res_ptr);
    assert(res_ptr != PTHREAD_CANCELED);
    int local_sum = ((result *) res_ptr)->sum;
    int error_code = ((result *) res_ptr)->error_code;
    delete (result *) res_ptr;
    if (error_code != NOERROR) return {sum, error_code};
    sum = update_sum(sum, local_sum);
    if (get_last_error() != NOERROR) return {sum, get_last_error()};
  }
  return {sum, NOERROR};
}

/**
 * start N threads and wait until they're done
 * return aggregated sum of values
 */
int run_threads(int &sum) {
  update current_update{0, true};

  std::vector<pthread_t> consumer_threads(n);
  int code = start_consumer_threads(consumer_threads, &current_update);
  if (code != 0) return code;

  pthread_t interruptor_thread;
  code = start_interruptor_thread(interruptor_thread, &consumer_threads);
  if (code != 0) return code;

  pthread_t producer_thread;
  code = start_producer_thread(producer_thread, &current_update);
  if (code != 0) return code;

  result result = get_result(consumer_threads);

  pthread_cond_destroy(&consumer_started_cond);
  pthread_cond_destroy(&update_ready_cond);
  pthread_cond_destroy(&update_processed_cond);
  pthread_mutex_destroy(&consumer_started_mutex);
  pthread_mutex_destroy(&update_mutex);

  sum = result.sum;
  return result.error_code;
}

int main(int argc, char *argv[]) {
  std::stringstream arg1(argv[1]);
  std::stringstream arg2(argv[2]);

  arg1 >> n;
  arg2 >> sleep_time_ms;
  int sum = 0;
  int error_code = run_threads(sum);
  if (error_code != NOERROR) {
    std::cout << "overflow" << std::endl;
    return 1;
  }
  std::cout << sum << std::endl;
  return 0;
}
