"""
run.py — Point d'entrée PyCharm
Double-cliquer pour lancer depuis PyCharm ou :
  python run.py
"""
import uvicorn
import os
from dotenv import load_dotenv

load_dotenv()

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=os.getenv("ML_SERVICE_HOST", "0.0.0.0"),
        port=int(os.getenv("ML_SERVICE_PORT", "8090")),
        reload=True,
        log_level="info",
    )