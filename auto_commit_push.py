import os
import subprocess
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

class CollectionFileHandler(FileSystemEventHandler):
    def on_created(self, event):
        if event.is_directory:
            return
        filepath = event.src_path
        if os.path.dirname(filepath) == "collections":
            subprocess.run(["git", "add", filepath])
            subprocess.run(["git", "commit", "-m", "Automatic commit of {}".format(filepath)])
            subprocess.run(["git", "push"])

if __name__ == "__main__":
    event_handler = CollectionFileHandler()
    observer = Observer()
    observer.schedule(event_handler, path="collections", recursive=True)
    observer.start()
    try:
        while True:
            pass
    except KeyboardInterrupt:
        observer.stop()
    observer.join()
