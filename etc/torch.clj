(require '[libpython-clj2.python :refer [py. py.. py.-] :as py])
; (py/initialize! :python-executable "/home/jan/venv/bin/python")
(require '[libpython-clj2.require :refer (require-python)])

(require-python '[tqdm :refer (tqdm)])
(require-python '[numpy :as np])
(require-python '[torch])
(require-python '[torch.nn :as nn])

(println (py. torch/cuda is_available))
(println (py. torch/cuda device_count))
(println
  (for [i (range (py. torch/cuda device_count))]
       (py. torch/cuda get_device_name i)))

(def a (np/array [[1 2 3] [4 5 6]]))
(def b (np/array [[1 2.5 3] [4 5 6]]))

(println (py.- a dtype))
(println (py.- b dtype))

(println (py.- a shape))
(println (py. a __add__ a))

(require-python '[operator :as op])
(op/add a a)

(System/exit 0)
