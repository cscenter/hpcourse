#include "mergesort.h"
#include "load_distribution.h"
#include "dfs.h"
#include "interruption.h"
#include <iostream>
#include <chrono>

using std::cout;
using std::endl;

int passed = 0, total = 0;

inline bool runAndCatch(std::function<void()> f) {
    try {
        f();
        return true;
    } catch (const std::exception &e) {
        cout << "Caught std::exception: " << e.what() << endl;
        return false;
    } catch (...) {
        cout << "Caught unknown exception" << endl;
        return false;
    }
}

template<typename Test, typename... Args>
void runTest(Args... args) {
    total++;
    cout << "===== " <<  Test::name(args...) << " =====" << endl;

    std::unique_ptr<Test> test;
    cout << "Setting up... ";
    if (!runAndCatch([&]() {
        test.reset(new Test(args...));
    })) {
        return;
    }
    cout << "OK" << endl;

    cout << "Running... ";
    auto start = std::chrono::steady_clock::now();
    bool ok;
    if (runAndCatch([&test, &ok](){
        ok = test->run();
    })) {
        if (ok) {
            cout << "OK" << endl;
        }
    } else {
        ok = false;
    }
    std::chrono::duration<double> diff = std::chrono::steady_clock::now() - start;
    cout << "Executed in " << diff.count() << " seconds" << endl;

    cout << "Tearing down... ";
    if (!runAndCatch([&]() {
        test.reset();
    })) {
        return;
    }
    cout << "OK" << endl;
    cout << endl;
    if (ok) {
        passed++;
    }
}

int main() {
    runTest<LoadDistributionTest>(1);
    runTest<LoadDistributionTest>(2);
    runTest<LoadDistributionTest>(3);
    runTest<LoadDistributionTest>(4);
    runTest<LoadDistributionTest>(10);
    runTest<LoadDistributionTest>(20);

    for (int i : { 1, 2, 3, 4, 10, 20 }) {
        runTest<DfsTest<false>>(i); // without exceptions
        runTest<DfsTest<true>>(i);  // with exceptions
    }

    runTest<InterruptionTest>();

    runTest<MergeSortTest>(1, 1);
    runTest<MergeSortTest>(1, 10);
    runTest<MergeSortTest>(4, 10);
    runTest<MergeSortTest>(128, 10);
    runTest<MergeSortTest>(int(1e4), 1);
    runTest<MergeSortTest>(int(1e4), 3);
    runTest<MergeSortTest>(int(1e4), 10);
    runTest<MergeSortTest>(int(2e8), 3);
    runTest<MergeSortTest>(int(2e8), 4);
    runTest<MergeSortTest>(int(2e8), 10);
    runTest<MergeSortTest>(int(2e8), 20);

    cout << "==========" << endl;
    cout << "Passed " << passed << " tests, failed " << total - passed << ", total " << total << endl;
    return passed == total ? 0 : 1;
}
