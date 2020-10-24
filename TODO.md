# TODO
* Get scale-image to work on large images
* implement fast version of slurp-shorts and slurp-floats

```
(def id (clojure.core.memoize/lu
        #(do (Thread/sleep 5000) (identity %))
    :lu/threshold 3))
```
