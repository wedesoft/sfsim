(require '[libpython-clj2.require :refer (require-python)]
         '[libpython-clj2.python :refer [py. py.. py.-] :as py])
(require-python '[numpy :as np])
(require-python '[torch])
(require-python '[torch.nn :as nn])

(py. torch/cuda is_available)
(py. torch/cuda device_count)
(for [i (range (py. torch/cuda device_count))]
      (py. torch/cuda get_device_name i))
