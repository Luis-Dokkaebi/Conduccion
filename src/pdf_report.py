import os
import matplotlib.pyplot as plt
from reportlab.lib.pagesizes import letter
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from datetime import datetime

def generate_shift_summary_pdf(driver_id: str, events: list, reached_red: bool):
    """
    Task 8.3: Generate Automated PDF Report
    Task 8.3.2: Generar gráficos de barras agrupando incidentes por hora
    Task 8.3.3: Enviar un reporte (...) catalogándolo como "PRIORITY REVIEW" si llegó a nivel rojo.
    """
    if not os.path.exists("reports"):
        os.makedirs("reports")

    date_str = datetime.now().strftime("%Y-%m-%d")
    pdf_path = f"reports/shift_summary_{driver_id}_{date_str}.pdf"

    c = canvas.Canvas(pdf_path, pagesize=letter)
    width, height = letter

    # 1. Header
    c.setFont("Helvetica-Bold", 16)
    c.drawString(50, height - 50, f"Driver Shift Summary: {driver_id}")

    c.setFont("Helvetica", 12)
    c.drawString(50, height - 80, f"Date: {date_str}")

    if reached_red:
        c.setFont("Helvetica-Bold", 14)
        c.setFillColorRGB(1, 0, 0)
        c.drawString(50, height - 110, "STATUS: PRIORITY REVIEW (FATIGUE RISK REACHED RED LEVEL)")
        c.setFillColorRGB(0, 0, 0)
    else:
        c.setFont("Helvetica", 12)
        c.drawString(50, height - 110, "STATUS: NORMAL")

    # 2. Group incidents by hour
    hours_counts = {str(i).zfill(2): 0 for i in range(24)}

    for event in events:
        ts = event.get("timestamp", 0)
        dt = datetime.fromtimestamp(ts / 1000.0) # Assuming ms
        hour_str = dt.strftime("%H")
        hours_counts[hour_str] += 1

    # 3. Create Matplotlib chart
    chart_path = f"reports/chart_{driver_id}.png"

    plt.figure(figsize=(8, 4))
    plt.bar(list(hours_counts.keys()), list(hours_counts.values()), color='blue')
    plt.title("Fatigue/Distraction Incidents per Hour")
    plt.xlabel("Hour of Day")
    plt.ylabel("Number of Incidents")
    plt.tight_layout()
    plt.savefig(chart_path)
    plt.close()

    # 4. Insert chart into PDF
    img = ImageReader(chart_path)
    c.drawImage(img, 50, height - 400, width=500, height=250)

    # 5. Clean up & Save
    c.save()
    if os.path.exists(chart_path):
        os.remove(chart_path)

    print(f"Generated PDF Report for {driver_id} at {pdf_path}")
    return pdf_path
