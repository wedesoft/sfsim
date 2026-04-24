# https://gemini.google.com/app/4a5ddfd717443d22
import gymnasium as gym
import numpy as np
import torch
from stable_baselines3 import PPO
from scipy.spatial.transform import Rotation

class BackwardsEntryEnv(gym.Env):
    def __init__(self):
        super().__init__()
        # Observation: [Height, Downrange, Velocity, Gamma]
        self.observation_space = gym.spaces.Box(low=-1e5, high=1e6, shape=(4,), dtype=np.float32)
        # Action: Bank angle normalized [-1, 1] mapped to [0, 90 degrees]
        self.action_space = gym.spaces.Box(low=-1, high=1, shape=(1,), dtype=np.float32)

        # Constants
        self.dt = -0.5 # Negative time step for backwards integration
        self.g = 9.81
        self.rho0 = 1.225
        self.H = 7500
        self.m = 100
        self.S = 0.5
        self.Cl = 0.5
        self.Cd = 0.2

    def reset(self, seed=None):
        # Start at landing: h=0, x=0, v=~100m/s, gamma=~-10 deg
        self.state = np.array([0.0, 0.0, 100.0, np.radians(-10.0)], dtype=np.float32)
        return self.state, {}

    def step(self, action):
        h, x, v, gamma = self.state
        bank = (action[0] + 1) * (np.pi / 4) # Map -1,1 to 0, 90 degrees

        # Physics
        rho = self.rho0 * np.exp(-h / self.H)
        q = 0.5 * rho * v**2
        L = q * self.S * self.Cl
        D = q * self.S * self.Cd

        # EOMs (Backwards)
        dh = v * np.sin(gamma) * self.dt
        dx = v * np.cos(gamma) * self.dt
        dv = (-D/self.m - self.g * np.sin(gamma)) * self.dt
        dgamma = ((L * np.cos(bank)) / (self.m * v) - (self.g * np.cos(gamma)) / v) * self.dt

        # Metrics for Reward
        g_force = np.sqrt(L**2 + D**2) / (self.m * self.g)
        energy_dot = (v * (-D/self.m - self.g * np.sin(gamma))) + (self.g * v * np.sin(gamma))

        self.state += np.array([dh, dx, dv, dgamma], dtype=np.float32)

        # --- Elegant Reward Function ---
        # 1. Penalize high G-loads (quadratic to punish the 'maximum')
        # 2. Penalize high energy gradients (smoothness)
        # 3. Reward staying "alive" (upward progress)
        reward = -0.1 * (g_force**2) - 0.001 * abs(energy_dot) + 1.0

        # Termination: Reach "Entry Interface" (e.g., 80km altitude)
        terminated = bool(self.state[0] >= 80000 or self.state[0] < -100)
        truncated = False

        return self.state, reward, terminated, truncated, {}

# --- Training ---
env = BackwardsEntryEnv()
model = PPO("MlpPolicy", env, verbose=1, learning_rate=3e-4)
model.learn(total_timesteps=50000)

print("Policy trained! The agent has learned to 'fall upwards' elegantly.")
