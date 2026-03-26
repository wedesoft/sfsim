import tqdm
import time
import numpy as np
import gymnasium as gym
import torch
from torch import nn
import torch.nn.functional as F


# Environment
class DiscreteActionWrapper(gym.ActionWrapper):
    "Bin continuous actions into discrete intervals."
    def __init__(self, env, n_actions=5):
        super().__init__(env)
        self.n_actions = n_actions
        self.action_space = gym.spaces.Discrete(n_actions)

    def action(self, action):
        if action == 0:
            return np.array([-2])
        elif action == 1:
            return np.array([-1])
        elif action == 2:
            return np.array([0])
        elif action == 3:
            return np.array([1])
        elif action == 4:
            return np.array([2])

class ObservationWrapper(gym.ObservationWrapper):
    "Scale the third value of the observation by 1/8."
    def __init__(self, env):
        super().__init__(env)

    def observation(self, observation):
        observation[2] *= 1/8.0
        return observation

class RewardScalingWrapper(gym.RewardWrapper):
    "Scale the reward by the given factor."
    def __init__(self, env, scaling_factor=1/10.0):
        super().__init__(env)
        self.scaling_factor = scaling_factor

    def reward(self, reward):
        return reward * self.scaling_factor

# Networks
class Policy(nn.Module):
    def __init__(self, input_size, n_actions):
        super().__init__()
        self.dense_1 = nn.Linear(input_size, hidden_size)
        self.dense_2 = nn.Linear(hidden_size, hidden_size//2)
        self.dense_3 = nn.Linear(hidden_size//2, n_actions)

    def forward(self, x):
        x = F.relu(self.dense_1(x))
        x = F.relu(self.dense_2(x))
        x = self.dense_3(x)
        return x

class ValueFunction(nn.Module):
    def __init__(self, input_size):
        super().__init__()
        self.dense_1 = nn.Linear(input_size, hidden_size)
        self.dense_2 = nn.Linear(hidden_size, 1)

    def forward(self, x):
        x = F.relu(self.dense_1(x))
        x = self.dense_2(x)
        return x


# Make an environment
env = gym.make('Pendulum-v1')
env = RewardScalingWrapper(DiscreteActionWrapper(ObservationWrapper(env)))

# 1. Initialize networks and old policy
hidden_size = 64
n_actions = env.action_space.n
input_size = env.observation_space.shape[0]

policy = Policy(input_size, n_actions)
value_function = ValueFunction(input_size)

old_policy = Policy(input_size, n_actions)
old_policy.load_state_dict(policy.state_dict())

# Define hyperparameters
device = 'cpu'
training_steps = 2000000
batch_size = 64
gamma = 0.99
epsilon = 0.2

learning_rate_policy = 0.0003
learning_rate_value = 0.001

optimizer_policy = torch.optim.Adam(policy.parameters(), lr=learning_rate_policy)
optimizer_value_function = torch.optim.Adam(value_function.parameters(), lr=learning_rate_value)

transitions = []

# Reset environment
state, info = env.reset()

# 2. Main training loop
for i in tqdm.tqdm(range(training_steps)):
    # 3. Gather trajectories
    # Convert state to a tensor
    state_tensor = torch.tensor(state, dtype=torch.float, device=device)

    # Pass state tensor through policy network
    logits = policy(state_tensor)

    # Convert logits to probability distribution
    dist = torch.distributions.Categorical(logits=logits)

    # Sample action from probability distribution
    action = dist.sample().item()

    # Execute action in environment
    next_state, reward, terminated, truncated, info = env.step(action)
    done = terminated or truncated

    transition = (state, action, reward, next_state, done)
    transitions.append(transition)

    # Reset environment if done
    if done:
        state, info = env.reset()
    else:
        state = next_state

    if len(transitions) < batch_size:
        continue

    # 4. Compute rewards to go
    # Expand transitions
    states, actions, rewards, next_states, dones = zip(*transitions)

    # Clear transitions
    transitions = []

    # If the final step didn't cause the environment to terminate or timeout
    if not dones[-1]:
        # Calculate the value of the final state
        final_state_tensor = torch.tensor(next_states[-1], dtype=torch.float, device=device)
        final_state_value = value_function(final_state_tensor).item()
    else:
        # If it did finish, there is no future discounted reward, so it's just 0
        final_state_value = 0

    # Initialize rewards to go list
    rewards_to_go = []

    # R represents all the future rewards
    # In the above equation, anything being multiplied by gamma is a part of R
    R = final_state_value

    # Iterate backwards
    for r, done in zip(reversed(rewards), reversed(dones)):
        # If the environment is done, there is no future reward
        if done:
            R = 0
        # Add the immediate reward and discount the future rewards
        R = r + gamma * R

        # Add the calculated reward-to-go to the list at the start as we're iterating backwards
        rewards_to_go.insert(0, R)

    # 5. Advantage estimate
    # Convert states to a tensor
    states_tensor = torch.tensor(np.array(states), dtype=torch.float, device=device)

    # Convert rewards-to-go to a tensor
    rewards_tensor = torch.tensor(np.array(rewards_to_go), dtype=torch.float, device=device)

    # Get state values with value function
    values = value_function(states_tensor).squeeze(-1)

    # Calculate advantages
    advantages = rewards_tensor - values

    # 6. Calculate policy ratio loss and backward pass
    # Convert actions to tensor and unsqueeze for upcoming gather
    actions_tensor = torch.tensor(actions, dtype=torch.long, device=device).unsqueeze(-1)

    # For current policy
    logits_current = policy(states_tensor)
    probs_current = F.softmax(logits_current, dim=-1)
    action_probs_current = probs_current.gather(dim=1, index=actions_tensor)

    # For old policy (use no_grad() as we never update the old policy with backprop)
    with torch.no_grad():
        logits_old = old_policy(states_tensor)
        probs_old = F.softmax(logits_old, dim=-1)
        action_probs_old = probs_old.gather(dim=1, index=actions_tensor)

    # Calculate the policy ratio
    policy_ratio = (action_probs_current/action_probs_old).squeeze(-1)

    # Next, calculate clipped policy ratio
    # This is a very simple calculation as we've already calculated the policy ratio
    clipped_policy_ratio = torch.clip(policy_ratio, min=1-epsilon, max=1+epsilon)

    # Next, we calculate the objective function
    objective = torch.min(policy_ratio * advantages, clipped_policy_ratio * advantages)

    # We then average this across all transitions and episodes using mean to get the policy loss
    # We use negative as we want to maximize it and loss metrics are to be minimized
    loss_policy = -torch.mean(objective)

    # First, set our old policy to our current policy before we update it
    old_policy.load_state_dict(policy.state_dict())

    # Backward pass for policy
    optimizer_policy.zero_grad()
    loss_policy.backward()
    optimizer_policy.step()

    # 7. Calculate value function loss and backward pass
    # Predict values for current state
    values = value_function(states_tensor).squeeze(-1)

    loss_value_function = F.mse_loss(values, rewards_tensor)

    # Backward pass for value function
    optimizer_value_function.zero_grad()
    loss_value_function.backward()
    optimizer_value_function.step()





# Create env
env = gym.make('Pendulum-v1', render_mode='human')
env = RewardScalingWrapper(DiscreteActionWrapper(ObservationWrapper(env)))


while True:
    # Reset env
    state, info = env.reset()
    total_reward = 0
    while True:
        state_tensor = torch.tensor(state, dtype=torch.float, device=device)
        logits = policy(state_tensor)
        dist = torch.distributions.Categorical(logits=logits)
        action = dist.sample().item()
        state, reward, terminated, truncated, _ = env.step(action)
        if terminated or truncated:
            break
    time.sleep(2)
