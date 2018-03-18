#include <pthread.h>

#include <iostream>
#include <string>
#include <vector>

using namespace std;


struct pc_buffer {
    int v;
    int accepted;
    pthread_cond_t *notFull, *notEmpty;
    pthread_mutex_t mutex;
};

pc_buffer the_buffer;

void init_pc_buffer(pc_buffer *buffer_p) {
    buffer_p->accepted = 1;
    buffer_p->notFull = (pthread_cond_t *)malloc(sizeof (pthread_cond_t));
    buffer_p->notEmpty = (pthread_cond_t *)malloc(sizeof(pthread_cond_t));
    pthread_cond_init(buffer_p->notFull, NULL);
    pthread_cond_init(buffer_p->notEmpty, NULL);
}

void write_to_buffer(pc_buffer *buffer_p, int value) {
    pthread_mutex_lock(&(buffer_p->mutex));

    while (!buffer_p->accepted) {
        pthread_cond_wait(buffer_p->notFull, &(buffer_p->mutex));
    }
    buffer_p->v = value;
    buffer_p->accepted = 0;

    pthread_mutex_unlock(&(buffer_p->mutex));
    pthread_cond_signal(buffer_p->notEmpty);
}

int read_from_buffer(pc_buffer *buffer_p) {
    int res;
    pthread_mutex_lock(&(buffer_p->mutex));
    while (buffer_p->accepted) {
       pthread_cond_wait(buffer_p->notEmpty, &(buffer_p->mutex));
    }
    buffer_p->accepted = 1;
    res = buffer_p->v;
    pthread_mutex_unlock(&(buffer_p->mutex));
    pthread_cond_signal(buffer_p->notFull);

    return res; 
}


void* producer(void *ptr) {
    string input;

    getline(cin, input);
    vector<int> numbers;
    size_t pos = 0;
    std::string token;
    string delimiter = ",";
    while ((pos = input.find(delimiter)) != std::string::npos) {
        token = input.substr(0, pos);
        input.erase(0, pos + delimiter.length());
        numbers.push_back(stoi(token));
    }
    numbers.push_back(stoi(input));

    write_to_buffer(&the_buffer, numbers.size());

    for(int i=0; i<numbers.size(); ++i) {
        write_to_buffer(&the_buffer, numbers[i]);
    }

    return 0;
}

void* consumer(void *ptr) {
     char *message;
     message = (char *) ptr;

     pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL); 

     int sz = read_from_buffer(&the_buffer);
     static int sum = 0;
     for (int i=0; i<sz; i++) {
         sum += read_from_buffer(&the_buffer);
     }

     int *res = new int[1];
     res[0] = sum; 

     pthread_exit(res); 
}

void* interruptor(void *ptr) {
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);

    pthread_t *other_p = (pthread_t*)ptr;
    int cnt = 0;
    while (1) {
        if (cnt++ % 1000 == 0) {
            pthread_testcancel();
        }
        pthread_cancel(*other_p);
    }
    return 0;
}

void run_threads() {
    init_pc_buffer(&the_buffer);

    int retcode;

    static pthread_t cons_thrd, prod_thrd, intr_thrd;

    retcode = pthread_create(&cons_thrd, NULL, consumer, NULL);
    if (retcode) {
        fprintf(stderr,"Error - pthread_create() return code: %d\n", retcode);
        exit(EXIT_FAILURE);
    }

    retcode = pthread_create(&intr_thrd, NULL, interruptor, (void*)&cons_thrd);
    if (retcode) {
        fprintf(stderr,"Error - pthread_create() return code: %d\n", retcode);
        exit(EXIT_FAILURE);
    }

    retcode = pthread_create(&prod_thrd, NULL, producer, NULL);
    if (retcode) {
        fprintf(stderr,"Error - pthread_create() return code: %d\n", retcode);
        exit(EXIT_FAILURE);
    }

    int *cons_res;
    int code = pthread_join(cons_thrd, (void**)&cons_res);
    if (cons_res == PTHREAD_CANCELED) {
        cout << "Sorry. Consumer was cancelled\n";
    } else {
        cout << *cons_res << "\n";
        delete []cons_res;
    }

    pthread_join(prod_thrd, NULL);
    pthread_cancel(intr_thrd);
    pthread_join(intr_thrd, NULL);
}


int main() {
  run_threads();
}
