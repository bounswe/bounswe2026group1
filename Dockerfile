# Build the image
docker build -t stub-app .

# Run it
docker run -p 8080:80 stub-app