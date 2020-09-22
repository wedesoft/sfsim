#version 330


#include semantic.glsl


// Incoming vertex position, Model Space
layout (location = POSITION) in vec2 position;

// Incoming vertex color
layout (location = COLOR) in vec3 color;


uniform GlobalMatrices
{
    mat4 view;
    mat4 proj;
};


// Uniform matrix from Model Space to camera (also known as view) Space
uniform mat4 model;


// Outgoing color for the next shader (fragment in this case)
out vec3 interpolatedColor;


void main() {

    // Normally gl_Position is in Clip Space and we calculate it by multiplying together all the matrices
    gl_Position = proj * (view * (model * vec4(position, 0, 1)));

    // We assign the color to the outgoing variable.
    interpolatedColor = color;
}
