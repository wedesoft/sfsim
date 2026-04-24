# export HIP_VISIBLE_DEVICES=
import gymnasium as gym
import numpy as np
import matplotlib.pyplot as plt
from stable_baselines3 import PPO


class SpeedModel(gym.Env):
    def __init__(self):
        super().__init__()
        self.observation_space = gym.spaces.Box(low=-1, high=1, shape=(2,), dtype=np.float32)
        self.action_space = gym.spaces.Box(low=-1, high=1, shape=(1,), dtype=np.float32)
        self.dt = 0.1

    def reset(self, seed=None):
        self.state = np.array([np.random.rand(1)[0] * 2 - 1, 0.0], dtype=np.float32)
        return self.state, {}

    def step(self, action):
        self.state[1] += 0.1 * action[0] * self.dt
        self.state[0] += self.state[1] * self.dt
        self.state = np.clip(self.state, -1, 1)
        reward = - np.abs(self.state[0]) - np.abs(self.state[1])
        done = abs(self.state[0]) < 0.01 and abs(self.state[1]) < 0.05
        info = {}
        return self.state, reward, done, False, info


env = SpeedModel()
model = PPO("MlpPolicy", env, verbose=1, learning_rate=0.001)
model.learn(total_timesteps=100000)


def test(env, model):
    obs = env.reset()[0]
    done = False
    total_reward = 0
    result = []
    for _ in range(64):
        if done:
            break
        action, _ = model.predict(obs)
        obs, reward, done, _, _ = env.step(action)
        result.append(float(obs[0]))
    return result


for _ in range(20):
    sequence = test(env, model)
    plt.plot(sequence)

plt.show()
