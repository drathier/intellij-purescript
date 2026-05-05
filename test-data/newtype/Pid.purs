module Pid where

import Foreign.Foreign (Foreign)

newtype Pid :: forall k. k -> Type
newtype Pid msg = Pid Foreign
