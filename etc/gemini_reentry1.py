# https://gemini.google.com/share/4662ee6d42a6
import numpy as np
import torch
import torch.nn as nn
from scipy.integrate import solve_ivp

# --- 1. Constants & Aero Model ---
G = 9.81            # m/s^2
RHO_0 = 1.225       # kg/m^3
H_SCALE = 7500      # Scale height (m)
S = 0.5             # Reference area (m^2)
M = 100             # Mass (kg)
CL = 0.5            # Lift coefficient
CD = 0.2            # Drag coefficient

def get_derivatives(t, state, bank_angle, direction=1):
    x, h, v, gamma = state
    rho = RHO_0 * np.exp(-h / H_SCALE)
    q = 0.5 * rho * v**2
    lift = q * S * CL
    drag = q * S * CD

    d_x = v * np.cos(gamma)
    d_h = v * np.sin(gamma)
    d_v = -drag/M - G * np.sin(gamma)
    d_gamma = (lift * np.cos(bank_angle)) / (M * v) - (G * np.cos(gamma)) / v

    return direction * np.array([d_x, d_h, d_v, d_gamma])

# --- 2. Backward Data Generation ---
def generate_data(num_samples=500):
    data_input = []
    data_label = []

    for _ in range(num_samples):
        # Sample random terminal conditions (landing)
        v_final = np.random.uniform(50, 150)
        gamma_final = np.random.uniform(np.radians(-20), np.radians(-5))
        bank_angle = np.random.uniform(0, np.radians(80)) # The "label"

        state_f = [0, 0, v_final, gamma_final] # x, h, v, gamma at landing

        # Integrate backwards for 100 seconds
        sol = solve_ivp(get_derivatives, [0, 100], state_f,
                        args=(bank_angle, -1), t_eval=np.linspace(0, 100, 20))

        if sol.success:
            for i in range(len(sol.y[0])):
                # Input: [h, x, v, gamma] | Label: [bank_angle]
                data_input.append(sol.y[:, i])
                data_label.append([bank_angle])

    return np.array(data_input), np.array(data_label)

# --- 3. Neural Network Definition ---
class GuidanceNN(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(4, 64),
            nn.ReLU(),
            nn.Linear(64, 64),
            nn.ReLU(),
            nn.Linear(64, 1)
        )

    def forward(self, x):
        return self.net(x)

# Prepare Data
X_raw, y_raw = generate_data()
# Note: In a real scenario, normalize X_raw here!
X = torch.tensor(X_raw, dtype=torch.float32)
y = torch.tensor(y_raw, dtype=torch.float32)

# --- 4. Training Loop ---
model = GuidanceNN()
optimizer = torch.optim.Adam(model.parameters(), lr=0.001)
criterion = nn.MSELoss()

print("Training started...")
for epoch in range(1000):
    optimizer.zero_grad()
    outputs = model(X)
    loss = criterion(outputs, y)
    loss.backward()
    optimizer.step()
    if epoch % 20 == 0:
        print(f"Epoch {epoch}, Loss: {loss.item():.6f}")
