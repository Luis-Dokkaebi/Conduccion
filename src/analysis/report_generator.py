# src/analysis/report_generator.py

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

class ReportGenerator:
    def __init__(self, results_df):
        self.df = results_df

    def generate_table(self):
        print("\n===== TABLA DE EFICIENCIA =====\n")
        print(self.df)

    def generate_bar_plot(self, save_path=None):
        plt.figure(figsize=(10, 6))
        
        # Determine X axis label
        if 'employee_name' in self.df.columns:
            # Create a combined label
            self.df['label'] = self.df.apply(lambda row: f"{row['track_id']} - {row['employee_name']}" if row['employee_name'] and row['employee_name'] != 'Unknown' else str(row['track_id']), axis=1)
            x_col = 'label'
            xlabel = "Empleado / ID"
        else:
            x_col = 'track_id'
            xlabel = "ID de Persona"

        # Si hay múltiples visitas, esto mostrará el promedio. Podríamos cambiar estimator=sum para total.
        sns.barplot(data=self.df, x=x_col, y="duration_sec", hue="zone", palette="viridis")
        plt.title("Duración Promedio de Estancia por Persona")
        plt.xlabel(xlabel)
        plt.ylabel("Duración (segundos)")
        plt.legend(title="Zona", bbox_to_anchor=(1.05, 1), loc='upper left')
        plt.tight_layout()
        if save_path:
            plt.savefig(save_path)
        plt.show()

    def export_to_excel(self, file_path=None):
        if file_path is None:
            from config import config
            import os
            file_path = os.path.join(config.REPORTS_DIR, "eficiencia.xlsx")
            os.makedirs(config.REPORTS_DIR, exist_ok=True)
        self.df.to_excel(file_path, index=False)
        print(f"Reporte exportado a: {file_path}")
